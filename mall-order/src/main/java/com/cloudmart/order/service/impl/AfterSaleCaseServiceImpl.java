package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.dto.AfterSaleCaseVO;
import com.cloudmart.order.dto.CreateAfterSaleRequest;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.entity.AfterSaleCaseEvent;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.repository.AfterSaleCaseEventMapper;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 售后案件服务实现（T04 结算核心）。
 *
 * <p>目标状态（方案 T04）：一笔退款申请绑定一个 case，服务端生成稳定退款号
 * {@code RFC{caseId}}；订单行锁内预占可退额度（锁顺序固定：订单行 → 案件行），
 * 拒绝/取消/关闭即释放预占；仅退款审批通过同事务写退款提交 Outbox（远程付款
 * 不嵌在长事务里），退货退款在质检通过后提交；订单履约状态不被一次部分退款
 * 覆盖（全额推进在订单侧 {@code onAfterSaleRefundCompleted} 汇总）。</p>
 *
 * <p>案件与订单解耦：apply 不改订单状态（订单仍 PAID/SHIPPED）。状态迁移全部
 * CAS（mapper 条件更新）+ 时间线留痕同事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleCaseServiceImpl implements com.cloudmart.order.service.AfterSaleCaseService {

    private static final Set<String> ALLOWED_TYPES = Set.of(AfterSaleCase.TYPE_REFUND_ONLY, AfterSaleCase.TYPE_RETURN_REFUND);
    private static final Set<String> APPLICABLE_ORDER_STATUS = Set.of("PAID", "SHIPPED");
    /** 数量占用口径：预占（PENDING/APPROVED）+ 已退（REFUNDED）都不可再次申请 */
    private static final Set<String> QUANTITY_OCCUPYING_STATUSES =
            Set.of(AfterSaleCase.STATUS_PENDING, AfterSaleCase.STATUS_APPROVED, AfterSaleCase.STATUS_REFUNDED);

    private final AfterSaleCaseMapper caseMapper;
    private final AfterSaleCaseEventMapper eventMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OutboxService outboxService;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    @Override
    @Transactional
    public AfterSaleCaseVO apply(Long userId, CreateAfterSaleRequest request) {
        Order order = orderMapper.selectById(request.orderId());
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作该订单");
        }
        if (!APPLICABLE_ORDER_STATUS.contains(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不支持申请售后");
        }
        if (!ALLOWED_TYPES.contains(request.type())) {
            throw new BusinessException("AFTER_SALE_TYPE_INVALID", "售后类型非法");
        }
        if ("SHIPPED".equals(order.getStatus()) && request.itemId() == null
                && AfterSaleCase.TYPE_RETURN_REFUND.equals(request.type())) {
            // 已发货整单退货退款首期不开放（需按行拆分），只允许仅退款
            throw new BusinessException("AFTER_SALE_TYPE_INVALID", "已发货订单请按商品项申请退货退款");
        }

        Long itemId = request.itemId();
        if (itemId != null) {
            OrderItem item = orderItemMapper.selectById(itemId);
            if (item == null || !order.getId().equals(item.getOrderId())) {
                throw new BusinessException("AFTER_SALE_ITEM_INVALID", "售后商品项不属于该订单");
            }
            validateItemQuantity(item, request.quantity());
        }

        Long open = caseMapper.selectCount(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, request.orderId())
                .eq(itemId != null, AfterSaleCase::getItemId, itemId)
                .eq(AfterSaleCase::getStatus, AfterSaleCase.STATUS_PENDING));
        if (open > 0) {
            throw new BusinessException("AFTER_SALE_DUPLICATE", "该订单已有待处理的售后申请");
        }

        AfterSaleCase entity = new AfterSaleCase();
        entity.setCaseNo("ASC" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        entity.setOrderId(request.orderId());
        entity.setUserId(userId);
        entity.setItemId(itemId);
        entity.setType(request.type());
        entity.setReason(request.reason());
        entity.setAttachmentFileIds(request.attachmentFileIds());
        entity.setQuantity(request.quantity() == null ? 0 : request.quantity());
        entity.setStatus(AfterSaleCase.STATUS_PENDING);
        caseMapper.insert(entity);
        appendEvent(entity.getId(), "APPLY", "user:" + userId,
                "{\"type\":\"" + request.type() + "\",\"orderId\":" + request.orderId()
                        + ",\"quantity\":" + entity.getQuantity() + "}");
        log.info("[T04] 售后案件已创建 caseNo={} orderId={} type={} itemId={} quantity={}",
                entity.getCaseNo(), request.orderId(), request.type(), itemId, entity.getQuantity());
        return toVO(entity, order.getOrderNo());
    }

    /**
     * T04 数量维度占用校验：同一订单项的可申请数量 = 明细数量 −（PENDING/APPROVED/REFUNDED
     * 案件已占数量）。两次申请不得同时占用同一件商品。
     */
    private void validateItemQuantity(OrderItem item, Integer requestedQuantity) {
        int requested = requestedQuantity == null ? 0 : requestedQuantity;
        if (requested < 1) {
            throw new BusinessException("AFTER_SALE_QUANTITY_INVALID", "售后数量必须为正整数");
        }
        if (requested > item.getQuantity()) {
            throw new BusinessException("AFTER_SALE_QUANTITY_INVALID",
                    "售后数量超过该明细购买数量（" + item.getQuantity() + "）");
        }
        int occupied = caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                        .eq(AfterSaleCase::getItemId, item.getId())
                        .in(AfterSaleCase::getStatus, QUANTITY_OCCUPYING_STATUSES))
                .stream()
                .mapToInt(c -> c.getQuantity() == null ? 0 : c.getQuantity())
                .sum();
        if (occupied + requested > item.getQuantity()) {
            throw new BusinessException("AFTER_SALE_QUANTITY_EXCEEDED",
                    "该明细可申请数量不足（已占用 " + occupied + "，共 " + item.getQuantity() + "）");
        }
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long caseId) {
        AfterSaleCase entity = requireCase(caseId);
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作该案件");
        }
        if (caseMapper.closeByOwner(caseId, userId) == 0) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "案件状态已变更，无法撤销");
        }
        appendEvent(caseId, "CLOSED", "user:" + userId, null);
    }

    @Override
    public Page<AfterSaleCaseVO> pageForUser(Long userId, String status, long page, long size) {
        LambdaQueryWrapper<AfterSaleCase> wrapper = new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getUserId, userId)
                .eq(status != null && !status.isBlank(), AfterSaleCase::getStatus, status)
                .orderByDesc(AfterSaleCase::getId);
        Page<AfterSaleCase> result = caseMapper.selectPage(new Page<>(page, size), wrapper);
        Page<AfterSaleCaseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(c -> toVO(c, orderNoOf(c.getOrderId()))).toList());
        return voPage;
    }

    @Override
    public java.util.List<AfterSaleCaseVO> listByOrderForUser(Long userId, Long orderId) {
        // T05：归属校验——订单必须是本人订单（防越权枚举他人订单售后）
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        return caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                        .eq(AfterSaleCase::getOrderId, orderId)
                        .orderByAsc(AfterSaleCase::getId))
                .stream().map(c -> toVO(c, order.getOrderNo())).toList();
    }

    @Override
    public AfterSaleCaseVO detail(Long userId, Long caseId) {
        AfterSaleCase entity = requireCase(caseId);
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权查看该案件");
        }
        return toVO(entity, orderNoOf(entity.getOrderId()));
    }

    @Override
    public Page<AfterSaleCaseVO> pageForAdmin(String status, Long orderId, long page, long size) {
        LambdaQueryWrapper<AfterSaleCase> wrapper = new LambdaQueryWrapper<AfterSaleCase>()
                .eq(status != null && !status.isBlank(), AfterSaleCase::getStatus, status)
                .eq(orderId != null, AfterSaleCase::getOrderId, orderId)
                .orderByDesc(AfterSaleCase::getId);
        Page<AfterSaleCase> result = caseMapper.selectPage(new Page<>(page, size), wrapper);
        Page<AfterSaleCaseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(c -> toVO(c, orderNoOf(c.getOrderId()))).toList());
        return voPage;
    }

    /**
     * T04 运营受理：订单行锁 → 金额上限核定（数量×单价实付分摊 + 订单级累计）→
     * CAS APPROVED（退款号服务端生成）→ 仅退款同事务写退款提交 Outbox。
     * 拒绝/撤销/关闭的案件不占用额度（占用口径 = APPROVED + REFUNDED）。
     */
    @Override
    @Transactional
    public AfterSaleCaseVO approve(Long adminId, Long caseId, BigDecimal refundAmount) {
        AfterSaleCase entity = requireCase(caseId);
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_INVALID", "批准退款金额必须为正");
        }
        if (!AfterSaleCase.STATUS_PENDING.equals(entity.getStatus())) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "案件状态已变更，无法受理");
        }
        // 锁顺序固定：先锁订单行（额度预占权威），后动案件行——并发受理串行化
        Order order = orderMapper.selectByIdForUpdate(entity.getOrderId());
        if (order == null || order.getPayAmount() == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在或金额缺失，无法受理退款");
        }
        BigDecimal orderPayAmount = validateRefundAmount(entity, order, refundAmount);

        String refundNo = refundNoOf(entity.getId());
        if (caseMapper.approve(caseId, refundAmount, refundNo, adminId) == 0) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "案件状态已变更，无法受理");
        }
        appendEvent(caseId, "APPROVE", "admin:" + adminId,
                "{\"refundNo\":\"" + refundNo + "\",\"refundAmount\":" + refundAmount.toPlainString()
                        + ",\"orderPayAmount\":" + orderPayAmount.toPlainString() + "}");

        if (AfterSaleCase.TYPE_REFUND_ONLY.equals(entity.getType())) {
            // T04：退款提交与审批同事务入 Outbox——远程付款不嵌在长事务；退货退款
            // 在质检通过（recordInspection PASSED）后提交
            outboxService.record(EventEnvelope.of("AFTER_SALE_REFUND_SUBMIT", 1,
                    String.valueOf(entity.getId()), 1, null, refundSubmitPayload(requireCase(caseId))));
        }
        log.info("[T04] 售后案件已受理 caseNo={} refundNo={} amount={}",
                entity.getCaseNo(), refundNo, refundAmount);
        return toVO(requireCase(caseId), order.getOrderNo());
    }

    /** T04：退款号由服务端从 caseId 派生——单 case 重试复用同号，追加退款新建 case */
    static String refundNoOf(Long caseId) {
        return "RFC" + caseId;
    }

    private String refundSubmitPayload(AfterSaleCase entity) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("caseId", entity.getId());
        payload.put("caseNo", entity.getCaseNo());
        payload.put("orderId", entity.getOrderId());
        payload.put("userId", entity.getUserId());
        payload.put("refundNo", entity.getRefundNo());
        payload.put("amount", entity.getRefundAmount() == null ? null : entity.getRefundAmount().toPlainString());
        payload.put("currency", "CNY");
        payload.put("afterSaleType", entity.getType());
        payload.put("itemId", entity.getItemId());
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * T04 金额上限（资金安全），锁内核算：
     * ① 行级——按申请数量 × 明细实付单价（pay_amount/quantity，历史单回退 price）
     *    核定，不得超过该行可退金额；
     * ② 订单级——同订单其他案件占用（APPROVED+REFUNDED）+ 本案 ≤ 订单实付。
     * 校验依据写入事件时间线供人工追溯。
     *
     * @return 订单实付金额（事件时间线记录校验依据用）
     */
    private BigDecimal validateRefundAmount(AfterSaleCase entity, Order order, BigDecimal refundAmount) {
        BigDecimal orderPayAmount = order.getPayAmount();
        if (AfterSaleCase.TYPE_RETURN_REFUND.equals(entity.getType()) && entity.getItemId() != null) {
            OrderItem item = orderItemMapper.selectById(entity.getItemId());
            if (item == null || item.getPrice() == null || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                throw new BusinessException("AFTER_SALE_AMOUNT_INVALID", "订单项缺失，无法核定退货退款金额");
            }
            BigDecimal caseQuantity = BigDecimal.valueOf(
                    entity.getQuantity() == null || entity.getQuantity() <= 0 ? 1 : entity.getQuantity());
            // 单价实付分摊优先（T04 落库字段），历史单未分摊回退成交价（订单级上限兜底）
            BigDecimal unitPaid = item.getPayAmount() != null
                    ? item.getPayAmount().divide(BigDecimal.valueOf(item.getQuantity()), 2,
                            java.math.RoundingMode.DOWN)
                    : item.getPrice();
            BigDecimal itemCap = unitPaid.multiply(caseQuantity);
            if (refundAmount.compareTo(itemCap) > 0) {
                throw new BusinessException("AFTER_SALE_AMOUNT_EXCEEDED",
                        "退款金额超过该商品行 " + caseQuantity.toPlainString() + " 件可退上限 ¥"
                                + itemCap.toPlainString());
            }
        }
        BigDecimal occupiedElsewhere = occupiedRefundAmount(entity.getOrderId(), entity.getId());
        if (occupiedElsewhere.add(refundAmount).compareTo(orderPayAmount) > 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_EXCEEDED",
                    "同订单累计退款将超过实付金额 ¥" + orderPayAmount.toPlainString()
                            + "（其他案件已占用 ¥" + occupiedElsewhere.toPlainString() + "）");
        }
        return orderPayAmount;
    }

    @Override
    @Transactional
    public AfterSaleCaseVO reject(Long adminId, Long caseId, String rejectReason) {
        AfterSaleCase entity = requireCase(caseId);
        if (caseMapper.reject(caseId, rejectReason, adminId) == 0) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "案件状态已变更，无法拒绝");
        }
        appendEvent(caseId, "REJECT", "admin:" + adminId, "{\"reason\":\""
                + (rejectReason == null ? "" : rejectReason.replace("\"", "'")) + "\"}");
        return toVO(requireCase(caseId), orderNoOf(entity.getOrderId()));
    }

    @Override
    public java.math.BigDecimal sumApprovedRefundAmounts(Long orderId) {
        return caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                        .eq(AfterSaleCase::getOrderId, orderId)
                        .in(AfterSaleCase::getStatus, AfterSaleCase.STATUS_APPROVED, AfterSaleCase.STATUS_REFUNDED))
                .stream()
                .map(AfterSaleCase::getRefundAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    @Override
    public BigDecimal occupiedRefundAmount(Long orderId, Long excludeCaseId) {
        return caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                        .eq(AfterSaleCase::getOrderId, orderId)
                        .ne(excludeCaseId != null, AfterSaleCase::getId, excludeCaseId)
                        .in(AfterSaleCase::getStatus, AfterSaleCase.STATUS_APPROVED, AfterSaleCase.STATUS_REFUNDED))
                .stream()
                .map(AfterSaleCase::getRefundAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional
    public void onRefundCompleted(String refundNo) {
        markRefundedReturning(refundNo);
    }

    @Override
    @Transactional
    public AfterSaleCase markRefundedReturning(String refundNo) {
        if (refundNo == null || refundNo.isBlank()) {
            return null;
        }
        if (caseMapper.markRefunded(refundNo) == 0) {
            // 重复/乱序通知或案件不存在：幂等无操作
            return null;
        }
        AfterSaleCase entity = caseMapper.selectOne(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getRefundNo, refundNo));
        if (entity != null) {
            appendEvent(entity.getId(), "REFUND_COMPLETED", "system", "{\"refundNo\":\"" + refundNo + "\"}");
            log.info("[T04] 售后案件退款完成回填 caseNo={} refundNo={} amount={}",
                    entity.getCaseNo(), refundNo, entity.getRefundAmount());
        }
        return entity;
    }

    @Override
    public AfterSaleCase findPendingWholeOrderCase(Long orderId) {
        return caseMapper.selectOne(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId)
                .isNull(AfterSaleCase::getItemId)
                .eq(AfterSaleCase::getStatus, AfterSaleCase.STATUS_PENDING)
                .orderByAsc(AfterSaleCase::getId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public void registerReturnShipping(Long userId, Long caseId, String carrier, String trackingNo) {
        AfterSaleCase entity = requireCase(caseId);
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作该案件");
        }
        if (caseMapper.registerReturnShipping(caseId, carrier, trackingNo) == 0) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "当前状态无法登记退货，或运单已登记");
        }
        appendEvent(caseId, "RETURN_SHIPPED", "user:" + userId,
                    "{\"carrier\":\"" + carrier + "\",\"trackingNo\":\"" + trackingNo + "\"}");
        log.info("[T04] 退货运单已登记 caseNo={} carrier={} trackingNo={}",
                 entity.getCaseNo(), carrier, trackingNo);
    }

    @Override
    @Transactional
    public void recordInspection(Long adminId, Long caseId, String result, String note) {
        if (!"PASSED".equals(result) && !"REJECTED".equals(result)) {
            throw new BusinessException("AFTER_SALE_INSPECT_INVALID", "质检结果非法（PASSED/REJECTED）");
        }
        if (caseMapper.recordInspection(caseId, result, note) == 0) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "当前状态无法录入质检（需已登记运单且未质检）");
        }
        appendEvent(caseId, "INSPECTED", "admin:" + adminId,
                    "{\"result\":\"" + result + "\"}");
        if ("PASSED".equals(result)) {
            AfterSaleCase entity = requireCase(caseId);
            // WMS：退货入库单自动创建（SKU 明细随事件携带）
            outboxService.record(EventEnvelope.of(
                    "AFTER_SALE_INSPECT_PASSED", 1, entity.getCaseNo(), 1, null,
                    inspectPassedPayload(entity)));
            // T04：验收通过即提交退款（RFC{caseId}，退款提交与 WMS 入库各自独立事实，
            // 一边失败不重复另一边）
            outboxService.record(EventEnvelope.of(
                    "AFTER_SALE_REFUND_SUBMIT", 1, String.valueOf(entity.getId()), 1, null,
                    refundSubmitPayload(entity)));
        }
        log.info("[T04] 售后质检录入 caseId={} result={} by admin:{}", caseId, result, adminId);
    }

    /**
     * 质检通过事件 payload：WMS 退货入库所需 SKU 明细。
     * quantity 缺省 1（单件退货）；itemId 关联的订单明细可能已被删除（数据完整性
     * 由消费者侧兜底校验），此处尽力携带。
     */
    private String inspectPassedPayload(AfterSaleCase entity) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("caseId", entity.getId());
        payload.put("caseNo", entity.getCaseNo());
        payload.put("orderId", entity.getOrderId());
        payload.put("userId", entity.getUserId());
        payload.put("afterSaleType", entity.getType());
        payload.put("itemId", entity.getItemId());
        payload.put("quantity", entity.getQuantity() == null ? 1 : entity.getQuantity());
        if (entity.getItemId() != null) {
            OrderItem item = orderItemMapper.selectById(entity.getItemId());
            if (item != null) {
                payload.put("skuId", item.getSkuId());
                payload.put("productName", item.getProductName());
            }
        }
        return objectMapper.writeValueAsString(payload);
    }

    @Override
    public ApiResponse<Boolean> hasOpenCase(Long orderId) {
        Long open = caseMapper.selectCount(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId)
                .in(AfterSaleCase::getStatus, List.of(AfterSaleCase.STATUS_PENDING, AfterSaleCase.STATUS_APPROVED)));
        return ApiResponse.ok(open > 0);
    }

    private AfterSaleCase requireCase(Long caseId) {
        AfterSaleCase entity = caseMapper.selectById(caseId);
        if (entity == null) {
            throw new BusinessException("AFTER_SALE_NOT_FOUND", "售后案件不存在");
        }
        return entity;
    }

    private void appendEvent(Long caseId, String action, String operator, String detail) {
        AfterSaleCaseEvent event = new AfterSaleCaseEvent();
        event.setCaseId(caseId);
        event.setAction(action);
        event.setOperator(operator);
        event.setDetail(detail);
        eventMapper.insert(event);
    }

    private String orderNoOf(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        return order == null ? null : order.getOrderNo();
    }

    private AfterSaleCaseVO toVO(AfterSaleCase entity, String orderNo) {
        List<AfterSaleCaseEvent> events = eventMapper.selectList(new LambdaQueryWrapper<AfterSaleCaseEvent>()
                .eq(AfterSaleCaseEvent::getCaseId, entity.getId())
                .orderByAsc(AfterSaleCaseEvent::getId));
        List<AfterSaleCaseVO.TimelineEntry> timeline = events.stream()
                .map(e -> new AfterSaleCaseVO.TimelineEntry(e.getAction(), e.getOperator(), e.getDetail(), e.getCreatedAt()))
                .toList();
        return new AfterSaleCaseVO(entity.getId(), entity.getCaseNo(), entity.getOrderId(), orderNo,
                entity.getUserId(), entity.getItemId(), entity.getType(), entity.getReason(),
                entity.getAttachmentFileIds(), entity.getQuantity(), entity.getStatus(),
                entity.getRefundNo(), entity.getRefundAmount(), entity.getRejectReason(),
                entity.getHandledAt(), entity.getCreatedAt(), timeline);
    }
}

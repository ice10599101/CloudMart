package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.dto.AfterSaleCaseVO;
import com.cloudmart.order.dto.AfterSaleEligibilityVO;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    /** TRADE-01：可申请订单状态——PAID/SHIPPED 不限时；COMPLETED 受 applyWindowDays 窗口约束 */
    private static final Set<String> APPLICABLE_ORDER_STATUS = Set.of("PAID", "SHIPPED", "COMPLETED");
    /** 数量占用口径：预占（PENDING/APPROVED）+ 已退（REFUNDED）都不可再次申请 */
    private static final Set<String> QUANTITY_OCCUPYING_STATUSES =
            Set.of(AfterSaleCase.STATUS_PENDING, AfterSaleCase.STATUS_APPROVED, AfterSaleCase.STATUS_REFUNDED);
    /** TRADE-01：请求键上限（超出按参数错误拒绝，防止超长键打爆唯一索引） */
    private static final int MAX_REQUEST_KEY_LENGTH = 128;

    private final AfterSaleCaseMapper caseMapper;
    private final AfterSaleCaseEventMapper eventMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OutboxService outboxService;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final com.cloudmart.order.config.AfterSaleProperties afterSaleProperties;

    @Override
    @Transactional
    public AfterSaleCaseVO apply(Long userId, CreateAfterSaleRequest request, String requestKey) {
        // TRADE-01：锁顺序 订单→案件/行项→事件——读取占用前先锁订单行，
        // 同一订单上的并发申请串行化（原实现"先读计数后插入"，两个请求会同时认为还有数量）
        Order order = orderMapper.selectByIdForUpdate(request.orderId());
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作该订单");
        }
        requireApplicableOrder(order);
        if (!ALLOWED_TYPES.contains(request.type())) {
            throw new BusinessException("AFTER_SALE_TYPE_INVALID", "售后类型非法");
        }
        // 可申请类型合同：整单只能仅退款（退货退款需按行拆分）；未发货订单只能仅退款
        if (request.itemId() == null && AfterSaleCase.TYPE_RETURN_REFUND.equals(request.type())) {
            throw new BusinessException("AFTER_SALE_TYPE_INVALID", "整单售后请选择仅退款，退货退款请按商品项申请");
        }
        if ("PAID".equals(order.getStatus()) && AfterSaleCase.TYPE_RETURN_REFUND.equals(request.type())) {
            throw new BusinessException("AFTER_SALE_TYPE_INVALID", "订单未发货，请选择仅退款");
        }

        // TRADE-01 请求事实键：同键同参返回原案件（不重复占用、不重复建案），同键异参 409
        String normalizedKey = normalizeRequestKey(requestKey);
        String payloadHash = normalizedKey == null ? null : payloadHashOf(userId, request);
        if (normalizedKey != null) {
            AfterSaleCase existing = findCaseByRequestKey(userId, normalizedKey);
            if (existing != null) {
                return replayExisting(existing, payloadHash, order.getOrderNo());
            }
        }

        // 有效状态占用（PENDING/APPROVED/REFUNDED）同时覆盖整单与按行：
        // 整单案件存在时不允许另开任何案件；已有行案件时不允许另开整单案件（重叠退款防护）
        List<AfterSaleCase> occupying = activeCasesOf(order.getId());
        boolean wholeOrderActive = occupying.stream().anyMatch(c -> c.getItemId() == null);
        if (wholeOrderActive) {
            throw new BusinessException("AFTER_SALE_DUPLICATE", "该订单已有整单售后处理中，不能再申请");
        }
        if (request.itemId() == null && !occupying.isEmpty()) {
            throw new BusinessException("AFTER_SALE_DUPLICATE", "该订单已有商品项售后处理中，不能申请整单售后");
        }

        Long itemId = request.itemId();
        OrderItem item = null;
        if (itemId != null) {
            item = orderItemMapper.selectById(itemId);
            if (item == null || !order.getId().equals(item.getOrderId())) {
                throw new BusinessException("AFTER_SALE_ITEM_INVALID", "售后商品项不属于该订单");
            }
            validateItemQuantity(item, request.quantity(), occupying);
        }

        AfterSaleCase entity = new AfterSaleCase();
        entity.setCaseNo("ASC" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        entity.setOrderId(request.orderId());
        entity.setUserId(userId);
        entity.setItemId(itemId);
        entity.setRequestKey(normalizedKey);
        entity.setPayloadHash(payloadHash);
        entity.setType(request.type());
        entity.setReason(request.reason());
        entity.setAttachmentFileIds(request.attachmentFileIds());
        entity.setQuantity(request.quantity() == null ? 0 : request.quantity());
        entity.setStatus(AfterSaleCase.STATUS_PENDING);
        try {
            caseMapper.insert(entity);
        } catch (DuplicateKeyException race) {
            // 并发同键竞态：uk(user_id, request_key) 兜底——按同键语义收敛（同参返回原案件，异参 409）
            AfterSaleCase existing = normalizedKey == null ? null : findCaseByRequestKey(userId, normalizedKey);
            if (existing != null && payloadHash != null) {
                return replayExisting(existing, payloadHash, order.getOrderNo());
            }
            throw race;
        }
        appendEvent(entity.getId(), "APPLY", "user:" + userId,
                "{\"type\":\"" + request.type() + "\",\"orderId\":" + request.orderId()
                        + ",\"quantity\":" + entity.getQuantity() + "}");
        log.info("[T04/TRADE-01] 售后案件已创建 caseNo={} orderId={} type={} itemId={} quantity={}",
                entity.getCaseNo(), request.orderId(), request.type(), itemId, entity.getQuantity());
        return toVO(entity, order.getOrderNo());
    }

    /** 归属状态 + COMPLETED 窗口校验（与 eligibility 同源） */
    private void requireApplicableOrder(Order order) {
        if (!APPLICABLE_ORDER_STATUS.contains(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不支持申请售后");
        }
        if ("COMPLETED".equals(order.getStatus()) && isWindowExpired(order)) {
            throw new BusinessException("AFTER_SALE_WINDOW_EXPIRED",
                    "已超过售后申请期限（确认收货后 " + afterSaleProperties.getApplyWindowDays() + " 天）");
        }
    }

    /** COMPLETED 售后窗口是否已过；completedAt 缺失（数据异常）按 fail-closed 处理 */
    private boolean isWindowExpired(Order order) {
        LocalDateTime completedAt = order.getCompletedAt();
        if (completedAt == null) {
            return true;
        }
        return LocalDateTime.now(ZoneOffset.UTC)
                .isAfter(completedAt.plusDays(afterSaleProperties.getApplyWindowDays()));
    }

    /** COMPLETED 窗口截止时间（其他状态为 null） */
    private LocalDateTime applyDeadlineOf(Order order) {
        if (!"COMPLETED".equals(order.getStatus()) || order.getCompletedAt() == null) {
            return null;
        }
        return order.getCompletedAt().plusDays(afterSaleProperties.getApplyWindowDays());
    }

    /** 请求键规范化：空白视为未提供；超长按参数错误拒绝 */
    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return null;
        }
        String trimmed = requestKey.trim();
        if (trimmed.length() > MAX_REQUEST_KEY_LENGTH) {
            throw new BusinessException("AFTER_SALE_REQUEST_KEY_INVALID", "幂等键过长（≤128 字符）");
        }
        return trimmed;
    }

    /** 请求载荷摘要：绑定订单/商品项/类型/数量/原因/附件（同键异参判定） */
    private String payloadHashOf(Long userId, CreateAfterSaleRequest request) {
        return sha256Hex(String.join("|",
                String.valueOf(userId), String.valueOf(request.orderId()),
                String.valueOf(request.itemId()), String.valueOf(request.type()),
                String.valueOf(request.quantity()),
                request.reason() == null ? "" : request.reason(),
                request.attachmentFileIds() == null ? "" : request.attachmentFileIds()));
    }

    private String sha256Hex(String canonical) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 同键重放收敛：同参返回原案件，异参拒绝 */
    private AfterSaleCaseVO replayExisting(AfterSaleCase existing, String payloadHash, String orderNo) {
        if (payloadHash == null || !payloadHash.equals(existing.getPayloadHash())) {
            throw new BusinessException("AFTER_SALE_REQUEST_CONFLICT",
                    "同一请求键的申请内容不同，请使用新键重新提交");
        }
        return toVO(existing, orderNo);
    }

    private AfterSaleCase findCaseByRequestKey(Long userId, String requestKey) {
        return caseMapper.selectOne(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getUserId, userId)
                .eq(AfterSaleCase::getRequestKey, requestKey)
                .last("LIMIT 1"));
    }

    /** 订单有效占用的案件（PENDING/APPROVED/REFUNDED；调用方持订单行锁） */
    private List<AfterSaleCase> activeCasesOf(Long orderId) {
        return caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId)
                .in(AfterSaleCase::getStatus, QUANTITY_OCCUPYING_STATUSES));
    }

    /**
     * TRADE-01 数量维度占用校验：同一订单项的可申请数量 = 明细数量 −（PENDING/APPROVED/REFUNDED
     * 案件已占数量）。有效占用包含整单与按行；订单行锁内调用，串行化并发申请。
     */
    private void validateItemQuantity(OrderItem item, Integer requestedQuantity, List<AfterSaleCase> occupying) {
        int requested = requestedQuantity == null ? 0 : requestedQuantity;
        if (requested < 1) {
            throw new BusinessException("AFTER_SALE_QUANTITY_INVALID", "售后数量必须为正整数");
        }
        if (requested > item.getQuantity()) {
            throw new BusinessException("AFTER_SALE_QUANTITY_INVALID",
                    "售后数量超过该明细购买数量（" + item.getQuantity() + "）");
        }
        int occupied = occupying.stream()
                .filter(c -> item.getId().equals(c.getItemId()))
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
        // TRADE-01：一致锁顺序 订单→案件→事件——与 apply/approve 在同一订单上串行化
        orderMapper.selectByIdForUpdate(entity.getOrderId());
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
    public AfterSaleEligibilityVO eligibility(Long userId, Long orderId, Long itemId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        final OrderItem item;
        if (itemId != null) {
            OrderItem loaded = orderItemMapper.selectById(itemId);
            if (loaded == null || !order.getId().equals(loaded.getOrderId())) {
                throw new BusinessException("AFTER_SALE_ITEM_INVALID", "售后商品项不属于该订单");
            }
            item = loaded;
        } else {
            item = null;
        }

        String status = order.getStatus();
        String rejectReason = null;
        if (!APPLICABLE_ORDER_STATUS.contains(status)) {
            rejectReason = "ORDER_STATUS_ERROR";
        } else if ("COMPLETED".equals(status) && isWindowExpired(order)) {
            rejectReason = "AFTER_SALE_WINDOW_EXPIRED";
        }

        // 有效占用（PENDING/APPROVED/REFUNDED）覆盖整单与按行——与 apply 校验同源
        List<AfterSaleCase> occupying = activeCasesOf(orderId);
        if (rejectReason == null) {
            boolean wholeOrderActive = occupying.stream().anyMatch(c -> c.getItemId() == null);
            if (wholeOrderActive) {
                rejectReason = "AFTER_SALE_DUPLICATE";
            } else if (item == null && !occupying.isEmpty()) {
                rejectReason = "AFTER_SALE_DUPLICATE";
            }
        }

        // 可申请类型（与 apply 同源）：整单/未发货 → 仅退款；已发货/已完成按商品项 → 退货退款或仅退款
        List<String> allowedTypes;
        if (item == null || "PAID".equals(status)) {
            allowedTypes = List.of(AfterSaleCase.TYPE_REFUND_ONLY);
        } else {
            allowedTypes = List.of(AfterSaleCase.TYPE_RETURN_REFUND, AfterSaleCase.TYPE_REFUND_ONLY);
        }

        Integer remainingQuantity = null;
        BigDecimal remainingRefundAmount;
        if (item != null) {
            int totalQty = item.getQuantity() == null ? 0 : item.getQuantity();
            int occupiedQty = occupying.stream()
                    .filter(c -> item.getId().equals(c.getItemId()))
                    .mapToInt(c -> c.getQuantity() == null ? 0 : c.getQuantity())
                    .sum();
            // 剩余可申请数量：PENDING/APPROVED/REFUNDED 一并占用（已退不再回到可申请池，与 apply 同源）
            remainingQuantity = Math.max(totalQty - occupiedQty, 0);
            long linePaidCents = item.getPayAmount() == null ? 0L : toCents(item.getPayAmount());
            long approvedCents = occupying.stream()
                    .filter(c -> item.getId().equals(c.getItemId()))
                    .filter(c -> AfterSaleCase.STATUS_APPROVED.equals(c.getStatus())
                            || AfterSaleCase.STATUS_REFUNDED.equals(c.getStatus()))
                    .map(AfterSaleCase::getRefundAmount)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(this::toCents)
                    .sum();
            remainingRefundAmount = fromCents(Math.max(linePaidCents - approvedCents, 0));
            if (rejectReason == null && remainingQuantity <= 0) {
                rejectReason = "AFTER_SALE_QUANTITY_EXCEEDED";
            }
        } else {
            BigDecimal occupied = occupiedRefundAmount(orderId, null);
            remainingRefundAmount = order.getPayAmount() == null
                    ? BigDecimal.ZERO
                    : order.getPayAmount().subtract(occupied).max(BigDecimal.ZERO);
        }
        return new AfterSaleEligibilityVO(orderId, itemId, rejectReason == null,
                allowedTypes, remainingQuantity, remainingRefundAmount, applyDeadlineOf(order), rejectReason);
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
        if (!AfterSaleCase.STATUS_PENDING.equals(entity.getStatus())) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "案件状态已变更，无法受理");
        }
        // 锁顺序固定：先锁订单行（额度预占权威），后动案件行——并发受理串行化
        Order order = orderMapper.selectByIdForUpdate(entity.getOrderId());
        if (order == null || order.getPayAmount() == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在或金额缺失，无法受理退款");
        }
        // R11：退款金额不允许为负；0 元仅适用于零元订单（全额优惠单售后：金额原路退 0，
        // 库存/券按既有全额退款规则处理，不调用收费渠道）
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_INVALID", "批准退款金额不能为负");
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) == 0
                && order.getPayAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_INVALID",
                    "0 元退款仅适用于零元订单；非零元订单退款金额必须为正");
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
            // 在质检通过（recordInspection PASSED）后提交。R11：0 元案同样出事件，
            // 由提交消费者识别并走域内零元结算，不调用收费渠道
            outboxService.record(EventEnvelope.of("AFTER_SALE_REFUND_SUBMIT", 1,
                    String.valueOf(entity.getId()), 1, null, refundSubmitPayload(requireCase(caseId))));
        }
        log.info("[T04/R11] 售后案件已受理 caseNo={} refundNo={} amount={}",
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
     * TRADE-01 金额上限（资金安全），锁内核算、整数分计算：
     * ① 行级（两种退款类型一致，原实现只约束 RETURN_REFUND）——
     *    行实付快照按件均分（floor），前几件按均分额度，最后剩余数量段可取
     *    行实付尾差；同时受"该行剩余可退金额"（行实付 − 该行已受理/已退金额）约束，
     *    任意阶段累计不超过行实付（10 元/3 件分批退累计恰为 10 元）；
     * ② 订单级——同订单其他案件占用（APPROVED+REFUNDED）+ 本案 ≤ 订单实付。
     * 金额一律取建单时冻结的 pay_amount 快照，不用现价/当前促销重算；
     * 无快照回退原价的旧分支已删除（新库无历史数据，缺失即数据异常 fail-closed）。
     *
     * @return 订单实付金额（事件时间线记录校验依据用）
     */
    private BigDecimal validateRefundAmount(AfterSaleCase entity, Order order, BigDecimal refundAmount) {
        BigDecimal orderPayAmount = order.getPayAmount();
        if (entity.getItemId() != null) {
            validateItemRefundCap(entity, refundAmount);
        }
        BigDecimal occupiedElsewhere = occupiedRefundAmount(entity.getOrderId(), entity.getId());
        if (occupiedElsewhere.add(refundAmount).compareTo(orderPayAmount) > 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_EXCEEDED",
                    "同订单累计退款将超过实付金额 ¥" + orderPayAmount.toPlainString()
                            + "（其他案件已占用 ¥" + occupiedElsewhere.toPlainString() + "）");
        }
        return orderPayAmount;
    }

    /** 行级可退上限（整数分）：数量均分 + 尾差归最后一个数量段 + 行剩余可退金额兜底 */
    private void validateItemRefundCap(AfterSaleCase entity, BigDecimal refundAmount) {
        OrderItem item = orderItemMapper.selectById(entity.getItemId());
        if (item == null || item.getPayAmount() == null
                || item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_INVALID", "订单项实付快照缺失，无法核定退款金额");
        }
        int totalQty = item.getQuantity();
        long linePayCents = toCents(item.getPayAmount());
        long unitCents = linePayCents / totalQty;
        long tailCents = linePayCents - unitCents * totalQty;
        // 行案件数量：整单语义（0/null）按整行数量处理（本方法仅行案件进入）
        int caseQty = entity.getQuantity() == null || entity.getQuantity() <= 0 ? totalQty : entity.getQuantity();

        // 该行已受理/已退（APPROVED/REFUNDED，不含本案）的数量与金额（调用方持订单行锁）
        List<AfterSaleCase> lineCases = caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getItemId, entity.getItemId())
                .ne(AfterSaleCase::getId, entity.getId())
                .in(AfterSaleCase::getStatus, AfterSaleCase.STATUS_APPROVED, AfterSaleCase.STATUS_REFUNDED));
        int lineOccupiedQty = lineCases.stream()
                .mapToInt(c -> c.getQuantity() == null || c.getQuantity() <= 0 ? totalQty : c.getQuantity())
                .sum();
        long lineOccupiedCents = lineCases.stream()
                .map(AfterSaleCase::getRefundAmount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(this::toCents)
                .sum();

        long baseCapCents = unitCents * caseQty;
        if (lineOccupiedQty + caseQty >= totalQty) {
            // 尾差归最后一个数量段：本次覆盖后该行数量全部进入"已受理/已退"口径
            baseCapCents += tailCents;
        }
        long lineRemainingCents = Math.max(linePayCents - lineOccupiedCents, 0);
        long capCents = Math.min(baseCapCents, lineRemainingCents);
        if (toCents(refundAmount) > capCents) {
            throw new BusinessException("AFTER_SALE_AMOUNT_EXCEEDED",
                    "退款金额超过该商品行可退上限（" + caseQty + " 件，上限 ¥"
                            + fromCents(capCents).toPlainString() + "）");
        }
    }

    /** 金额转整数分（DECIMAL(10,2) 语义；超过两位小数按参数错误拒绝） */
    private long toCents(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException("AFTER_SALE_AMOUNT_INVALID", "金额精度非法（最多两位小数）");
        }
    }

    private BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    @Override
    @Transactional
    public AfterSaleCaseVO reject(Long adminId, Long caseId, String rejectReason) {
        AfterSaleCase entity = requireCase(caseId);
        // TRADE-01：一致锁顺序 订单→案件→事件（释放占用前先与同订单其他申请串行化）
        orderMapper.selectByIdForUpdate(entity.getOrderId());
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

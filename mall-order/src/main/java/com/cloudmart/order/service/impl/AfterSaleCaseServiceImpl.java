package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
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
 * 售后案件服务实现（T11 切片一）。
 *
 * <p>案件与订单解耦：apply 不改订单状态（订单仍 PAID/SHIPPED），受理后才走
 * T02 退款链路推进订单——避免"申请即锁单"被恶意刷。同单同项 PENDING 唯一
 * （重复申请在服务层拒绝）。状态迁移全部 CAS（mapper 条件更新）+ 时间线留痕同事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleCaseServiceImpl implements com.cloudmart.order.service.AfterSaleCaseService {

    private static final Set<String> ALLOWED_TYPES = Set.of(AfterSaleCase.TYPE_REFUND_ONLY, AfterSaleCase.TYPE_RETURN_REFUND);
    private static final Set<String> APPLICABLE_ORDER_STATUS = Set.of("PAID", "SHIPPED");

    private final AfterSaleCaseMapper caseMapper;
    private final AfterSaleCaseEventMapper eventMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final com.cloudmart.common.async.outbox.OutboxService outboxService;

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
                "{\"type\":\"" + request.type() + "\",\"orderId\":" + request.orderId() + "}");
        log.info("[T11] 售后案件已创建 caseNo={} orderId={} type={}", entity.getCaseNo(), request.orderId(), request.type());
        return toVO(entity, order.getOrderNo());
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

    @Override
    @Transactional
    public AfterSaleCaseVO approve(Long adminId, Long caseId, BigDecimal refundAmount, String refundNo) {
        AfterSaleCase entity = requireCase(caseId);
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("AFTER_SALE_AMOUNT_INVALID", "批准退款金额必须为正");
        }
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException("AFTER_SALE_REFUND_NO_REQUIRED", "受理必须关联退款单号（T02）");
        }
        if (caseMapper.approve(caseId, refundAmount, refundNo, adminId) == 0) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "案件状态已变更，无法受理");
        }
        appendEvent(caseId, "APPROVE", "admin:" + adminId,
                "{\"refundNo\":\"" + refundNo + "\",\"refundAmount\":" + refundAmount.toPlainString() + "}");
        log.info("[T11] 售后案件已受理 caseNo={} refundNo={} amount={}", entity.getCaseNo(), refundNo, refundAmount);
        return toVO(requireCase(caseId), orderNoOf(entity.getOrderId()));
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
    @Transactional
    public void onRefundCompleted(String refundNo) {
        if (caseMapper.markRefunded(refundNo) == 1) {
            AfterSaleCase entity = caseMapper.selectOne(new LambdaQueryWrapper<AfterSaleCase>()
                    .eq(AfterSaleCase::getRefundNo, refundNo));
            if (entity != null) {
                appendEvent(entity.getId(), "REFUND_COMPLETED", "system", "{\"refundNo\":\"" + refundNo + "\"}");
                log.info("[T11] 售后案件退款完成回填 caseNo={} refundNo={}", entity.getCaseNo(), refundNo);
            }
        }
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
        log.info("[T11] 退货运单已登记 caseNo={} carrier={} trackingNo={}",
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
        // T11 切片二 C：质检 PASSED → 自动退款流转（Outbox 事件驱动本服务消费者，
        // 系统代发 requestRefund + approveRefund 免人工二次操作；REJECTED 保持
        // APPROVED 等待运营与用户协商）
        if ("PASSED".equals(result)) {
            AfterSaleCase entity = requireCase(caseId);
            outboxService.record(com.cloudmart.common.async.EventEnvelope.of(
                    "AFTER_SALE_INSPECT_PASSED", 1, entity.getCaseNo(), 1, null,
                    "{\"caseId\":" + caseId + ",\"caseNo\":\"" + entity.getCaseNo()
                            + "\",\"orderId\":" + entity.getOrderId()
                            + ",\"userId\":" + entity.getUserId() + "}"));
        }
        log.info("[T11] 售后质检录入 caseId={} result={} by admin:{}", caseId, result, adminId);
    }

    @Override
    public com.cloudmart.order.entity.AfterSaleCase findApprovedWithoutRefundNo(Long orderId) {
        return caseMapper.selectOne(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId)
                .eq(AfterSaleCase::getStatus, AfterSaleCase.STATUS_APPROVED)
                .isNull(AfterSaleCase::getRefundNo)
                .orderByAsc(AfterSaleCase::getId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public void bindRefundNo(Long caseId, String refundNo) {
        AfterSaleCase entity = requireCase(caseId);
        int updated = caseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AfterSaleCase>()
                .set(AfterSaleCase::getRefundNo, refundNo)
                .eq(AfterSaleCase::getId, caseId)
                .eq(AfterSaleCase::getStatus, AfterSaleCase.STATUS_APPROVED)
                .isNull(AfterSaleCase::getRefundNo));
        if (updated > 0) {
            appendEvent(caseId, "BIND_REFUND", "system", "{\"refundNo\":\"" + refundNo + "\"}");
        }
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

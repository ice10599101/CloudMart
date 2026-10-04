package com.cloudmart.order.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.AfterSaleCaseVO;
import com.cloudmart.order.dto.CreateAfterSaleRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 售后案件服务（T11 切片一）：申请/撤销/查询（用户侧）与受理/拒绝（运营侧）。
 * 批准后走 T02 退款单执行资金退回；退款完成事件回填 REFUNDED。
 */
public interface AfterSaleCaseService {

    /** 用户申请售后：PAID/SHIPPED 可申请；未发货=仅退款，已发货=退货退款或仅退款；同单同项 PENDING 唯一 */
    AfterSaleCaseVO apply(Long userId, CreateAfterSaleRequest request);

    /** 用户撤销 PENDING 申请 */
    void cancel(Long userId, Long caseId);

    /** 用户自己的案件详情（含时间线） */
    AfterSaleCaseVO detail(Long userId, Long caseId);

    /** 运营分页（状态/订单号筛选） */
    Page<AfterSaleCaseVO> pageForAdmin(String status, Long orderId, long page, long size);

    /**
     * 运营受理（T04）：PENDING → APPROVED，订单行锁内预占可退额度并核定金额上限，
     * 服务端生成稳定退款号 RFC{caseId}（后台不再输入 refundNo）；REFUND_ONLY 同事务
     * 写退款提交 Outbox（退货退款在质检通过后提交）。
     */
    AfterSaleCaseVO approve(Long adminId, Long caseId, java.math.BigDecimal refundAmount);

    /** 运营拒绝：PENDING → REJECTED */
    AfterSaleCaseVO reject(Long adminId, Long caseId, String rejectReason);

    /** T11：本订单已批准售后案件退款金额合计（APPROVED 占用中 + REFUNDED 已退）；无案件返回 null */
    java.math.BigDecimal sumApprovedRefundAmounts(Long orderId);

    /** T02 退款完成事件回填：APPROVED → REFUNDED（消费者按 refundNo 幂等） */
    void onRefundCompleted(String refundNo);

    /**
     * 内部：订单是否存在未结售后案件（PENDING/APPROVED）——注销/重新申请校验用。
     */
    ApiResponse<Boolean> hasOpenCase(Long orderId);

    /** T11 切片二：用户登记退货运单（RETURN_REFUND 且 APPROVED；运单唯一） */
    void registerReturnShipping(Long userId, Long caseId, String carrier, String trackingNo);

    /** T11 切片二：质检结果录入（人工；PASSED/REJECTED） */
    void recordInspection(Long adminId, Long caseId, String result, String note);

    /**
     * T04 退款完成回填：CAS APPROVED → REFUNDED 并返回案件（含金额）；
     * 重复/乱序通知返回 null（幂等），调用方不再推进订单汇总。
     */
    com.cloudmart.order.entity.AfterSaleCase markRefundedReturning(String refundNo);

    /** T04：订单下 PENDING 的整单售后案件（itemId 为空；旧退款入口适配用） */
    com.cloudmart.order.entity.AfterSaleCase findPendingWholeOrderCase(Long orderId);

    /** T04：查询订单下在途/已退占用的可退余额（实付 - 已占用），锁定语义由调用方保证 */
    java.math.BigDecimal occupiedRefundAmount(Long orderId, Long excludeCaseId);
}

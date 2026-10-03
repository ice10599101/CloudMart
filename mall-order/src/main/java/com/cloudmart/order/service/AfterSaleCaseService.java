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

    /** 运营受理：PENDING → APPROVED，冻结批准金额，关联/创建 T02 退款单 */
    AfterSaleCaseVO approve(Long adminId, Long caseId, java.math.BigDecimal refundAmount, String refundNo);

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

    /** T11：查订单下 APPROVED 且尚未关联退款单的案件（approveRefund 时回填关联） */
    com.cloudmart.order.entity.AfterSaleCase findApprovedWithoutRefundNo(Long orderId);

    /** T11：回填退款单号（APPROVED 状态 CAS） */
    void bindRefundNo(Long caseId, String refundNo);
}

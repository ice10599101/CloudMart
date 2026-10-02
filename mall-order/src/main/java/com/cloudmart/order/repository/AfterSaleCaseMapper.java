package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.AfterSaleCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 售后案件 Mapper（T11）：状态迁移全部 CAS 条件更新——用户撤销、运营受理、
 * 退款回填并发时只有一方生效，重复消息/重复点击无害。
 */
@Mapper
public interface AfterSaleCaseMapper extends BaseMapper<AfterSaleCase> {

    /** CAS PENDING → APPROVED（运营受理并冻结批准金额） */
    @Update("UPDATE after_sale_case SET status = 'APPROVED', refund_amount = #{refundAmount}, "
            + "refund_no = #{refundNo}, handled_by = #{adminId}, handled_at = NOW(3) "
            + "WHERE id = #{caseId} AND status = 'PENDING'")
    int approve(@Param("caseId") Long caseId, @Param("refundAmount") BigDecimal refundAmount,
                @Param("refundNo") String refundNo, @Param("adminId") Long adminId);

    /** CAS PENDING → REJECTED（运营拒绝留原因） */
    @Update("UPDATE after_sale_case SET status = 'REJECTED', reject_reason = #{rejectReason}, "
            + "handled_by = #{adminId}, handled_at = NOW(3) "
            + "WHERE id = #{caseId} AND status = 'PENDING'")
    int reject(@Param("caseId") Long caseId, @Param("rejectReason") String rejectReason,
               @Param("adminId") Long adminId);

    /** CAS APPROVED → REFUNDED（T02 退款完成事件回填；重复回填无害） */
    @Update("UPDATE after_sale_case SET status = 'REFUNDED' "
            + "WHERE refund_no = #{refundNo} AND status = 'APPROVED'")
    int markRefunded(@Param("refundNo") String refundNo);

    /** CAS PENDING → CLOSED（用户撤销申请） */
    @Update("UPDATE after_sale_case SET status = 'CLOSED' "
            + "WHERE id = #{caseId} AND user_id = #{userId} AND status = 'PENDING'")
    int closeByOwner(@Param("caseId") Long caseId, @Param("userId") Long userId);
}

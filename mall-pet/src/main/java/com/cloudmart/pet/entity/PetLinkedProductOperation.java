package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品权益核销操作台账（R01）：每次核销请求一行，跨服务核验超时留 PENDING，
 * 按 operationId 重查/后台原单重试；拒绝有原因码，绝不跳过资格校验直接造权益。
 *
 * <p>幂等：uk(user_id, request_key) 请求重放返回原操作；"核销事实唯一"由
 * pet_feed_entitlement 的 uk(redemption_code)/uk(source_fact_id, unit_sequence) 保证——
 * 同一码至多发放一次权益。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_linked_product_operation")
public class PetLinkedProductOperation {

    public static final String STATUS_VERIFYING = "VERIFYING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_PENDING = "PENDING";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationId;

    private String requestKey;

    private Long userId;

    private String redemptionCode;

    /** 核验通过后回填：coupon exchange_codes.id */
    private Long couponFactId;

    private Long couponTemplateId;

    /** 核销成功后发放的权益 ID */
    private Long entitlementId;

    private String status;

    /** 拒绝原因码（PET_CODE_*） */
    private String rejectReasonCode;

    /** 最近一次核验/发放错误（PENDING 恢复依据） */
    private String lastError;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

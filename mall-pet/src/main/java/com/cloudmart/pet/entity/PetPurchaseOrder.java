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
 * 宠物币购买订单（W01）：服务端权威价格快照；实际履约以 asset_grant(source=ORDER) 为准。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_purchase_order")
public class PetPurchaseOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 宠物 ID（可空） */
    private Long petId;

    /** 物品类型 */
    private String itemType;

    /** 物品编码 */
    private String itemCode;

    /** 数量（首版固定 1） */
    private Integer quantity;

    /** 单价快照 */
    private Long unitPrice;

    /** 实扣总额 */
    private Long totalAmount;

    /** 币种 */
    private String currency;

    /** 钱包域（PET） */
    private String walletDomain;

    /** 商品配置版本 */
    private String configVersion;

    /** 物品快照 JSON */
    private String itemSnapshot;

    /** 状态：PROCESSING/COMPLETED/REJECTED */
    private String status;

    /** 扣款流水 ID */
    private Long walletTransactionId;

    /** 客户端请求键（P02：uk(user_id,request_key) 兜底防重复建单；重试/接管复用同一订单） */
    private String requestKey;

    /** 规范请求摘要（P02：与 dedup 行核对，防止业务事实与幂等事实错位） */
    private String payloadHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 终态时间（UTC） */
    private LocalDateTime completedAt;
}
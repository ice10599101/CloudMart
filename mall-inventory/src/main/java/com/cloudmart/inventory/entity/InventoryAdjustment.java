package com.cloudmart.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存调整单（TRADE-02）：库存唯一受控变更入口（初始化一次 + 补货走入库 + 盘点走本表）。
 * <p>request_key 幂等（同键同参返回原单，同键异参拒绝）；前后值快照全量审计。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("inventory_adjustment")
public class InventoryAdjustment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调整单号（ADJ+yyyyMMdd+6 位随机段，uk 兜底） */
    private String adjustNo;

    private Long skuId;

    /** 商品 ID（冗余，便于检索） */
    private Long productId;

    /** 调整量：正盘盈 / 负盘亏，恒非 0 */
    private Integer delta;

    /** 调整原因（盘点/损耗/纠错等） */
    private String reason;

    /** 幂等键：客户端生成；同键同参返回原单 */
    private String requestKey;

    /** 提交时期望的乐观锁版本（CAS 前置条件） */
    private Integer expectedVersion;

    private Integer beforeAvailable;

    private Integer afterAvailable;

    /** 调整只动 available，reserved 仅作快照审计 */
    private Integer beforeReserved;

    private Integer afterReserved;

    /** 操作管理员 ID */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

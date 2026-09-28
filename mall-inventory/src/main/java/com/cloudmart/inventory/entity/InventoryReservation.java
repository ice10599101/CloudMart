package com.cloudmart.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存预占台账（STOCK-01）：DB 为权威记录，Redis 仅作加速。
 * 状态机 RESERVED → CONFIRMED / RELEASED 由条件更新保证一次性。
 */
@Data
@TableName("inventory_reservation")
public class InventoryReservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    private Integer quantity;

    /** RESERVED / CONFIRMED / RELEASED */
    private String status;

    private Integer version;

    private LocalDateTime confirmedAt;

    private LocalDateTime releasedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

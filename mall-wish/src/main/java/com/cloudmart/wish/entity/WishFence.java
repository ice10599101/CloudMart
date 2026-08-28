package com.cloudmart.wish.entity;

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
 * 地理围栏（Sprint 3.2，文档 1.2 ⑯ wish_fence）。
 *
 * <p>隐私边界：centerGeohash 仅服务端存储，用户端 API 永不回传
 * （客户端仅感知"到达/未到达"）；半径最小 10m；判定含等号
 * （Haversine ≤ radius → 命中，验收：距离 = radius → true）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_fence")
public class WishFence {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 围栏名称（如"老城书店"） */
    private String name;

    /** 到达后触发绽放的心愿 ID */
    private Long wishId;

    /** 围栏中心 geohash7（服务端存储，客户端不可见） */
    private String centerGeohash;

    /** 半径（米，最小 10） */
    private Integer radiusM;

    /** 生效开始（UTC，NULL=不限） */
    private LocalDateTime validFrom;

    /** 生效结束（UTC，NULL=不限） */
    private LocalDateTime validTo;

    /** 是否启用 */
    private Boolean isActive;

    /** 创建管理员（管理后台用户 ID） */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

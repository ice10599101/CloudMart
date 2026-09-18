package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 礼物目录实体（V37 迁移，全站虚拟礼物）。
 *
 * <p>管理后台维护：名称/图标/星光单价/上下架/排序。仅 {@code ON_SHELF}
 * 对用户端礼物选择器可见；软删保留审计轨迹。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_gift")
public class Gift {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 礼物名称 */
    private String name;

    /** 礼物图标 URL（空时前端展示默认礼物图标） */
    private String iconUrl;

    /** 礼物动效资源 URL（可选，送出时播放） */
    private String animationUrl;

    /** 单价（星光，正整数） */
    private Integer priceStarlight;

    /** 状态：ON_SHELF 上架 / OFF_SHELF 下架 */
    private String status;

    /** 排序值（越小越靠前） */
    private Integer sort;

    /** 礼物描述（可选） */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}

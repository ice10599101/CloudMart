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

@Getter
@Setter
@NoArgsConstructor
@TableName("wish_badge")
public class WishBadge {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String icon;

    /** 稀有度（V5）：COMMON/RARE/EPIC/LEGENDARY */
    private String rarity;

    /** 上架状态（V6）：0=下架（不参与判定、不出现在徽章墙/图鉴） */
    private Boolean isActive;

    /** 触发条件 JSON；CONDITION 为 MySQL 保留字，列名必须反引号转义 */
    @TableField("`condition`")
    private String condition;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

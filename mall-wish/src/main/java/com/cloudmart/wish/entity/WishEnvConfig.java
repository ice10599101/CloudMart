package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.EnvCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 环境配置实体（Sprint 2.2 环境配置表化，V10 迁移）。
 *
 * <p>每个环境一行（天气/季节/时段/特殊事件），{@code visual} JSON 为
 * 四端渲染参数透传载体；新增"中秋"等环境仅需插入配置行不改代码
 * （文档 Sprint 2.2 验收：环境配置表化）。{@code is_active}=0 下架后
 * 读取方过滤（与徽章 is_active 同语义）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_env_config")
public class WishEnvConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 环境代码（唯一；SUNNY/RAIN/SPRING/METEOR_SHOWER/...） */
    private String envCode;

    /** 环境分类（仅管理端分组展示） */
    private EnvCategory category;

    private String name;

    private String description;

    /** 渲染优先级（数值大者胜：特殊事件 100/情绪 80/天气 50/季节 30/时段 10） */
    private Integer priority;

    /** 四端渲染视觉参数 JSON（crownColor/skyColor/particle/lightCoreColor 等） */
    private String visual;

    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

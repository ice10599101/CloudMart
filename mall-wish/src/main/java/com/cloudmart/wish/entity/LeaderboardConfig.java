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
 * 排行榜配置（Sprint 2.7，文档 2.7 管理后台：榜单规则/计算周期/同分处理）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_leaderboard_config")
public class LeaderboardConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键（如 lb.refresh_minutes） */
    private String configKey;

    /** 配置值（字符串，业务层解析） */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 最后修改人（管理后台用户 ID） */
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

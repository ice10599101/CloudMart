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
 * AI/提醒策略全局配置（Sprint 2.5，文档 2.5 管理后台）。
 *
 * <p>管理后台修改后运行时实时生效（Service 层短 TTL 缓存 + 更新时主动失效）。
 * 值统一存字符串，业务层按配置键解析类型。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_ai_config")
public class WishAiConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键（如 reminder.daily_limit） */
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

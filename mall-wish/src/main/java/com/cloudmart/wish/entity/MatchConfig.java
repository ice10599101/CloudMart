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
 * 匹配算法配置（Sprint 2.6，文档 2.6 验收：权重可配置不改代码）。
 *
 * <p>管理后台修改后运行时实时生效（60s 快照缓存 + 更新主动失效）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_match_config")
public class MatchConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键（如 match.weight_keyword） */
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

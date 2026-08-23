package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.enums.AiPromptStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI Prompt 模板版本管理（Sprint 2.5，文档 2.5 管理后台）。
 *
 * <p>调整 Prompt 不改代码不重部署：运行时按 scene 读取 ACTIVE 模板，
 * 同 scene 多条 ACTIVE 按 traffic_percent 加权分流（A/B 测试）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_ai_prompt")
public class WishAiPrompt {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private AiPromptScene scene;

    /** 版本号（scene 内递增） */
    private Integer version;

    /** 模板名称（管理后台展示） */
    private String name;

    /** Prompt 正文（支持 {placeholder} 变量） */
    private String content;

    /** A/B 分组（ALL=不分流） */
    private String abGroup;

    /** 流量百分比（1-100） */
    private Integer trafficPercent;

    private AiPromptStatus status;

    /** 备注（版本变更说明） */
    private String remark;

    /** 创建人（管理后台用户 ID） */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

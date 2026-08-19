package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 对话历史（文档 1.2 节 ㊲c，树洞 / AI 助手 / 年度报告）。
 *
 * <p>每条记录对应一个角色的一条消息；同一会话（session_id）含 USER 与 ASSISTANT
 * 交替记录。树洞场景会话标识：{@code tree-hole-{wishId}-{userId}}。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_ai_conversation")
public class WishAiConversation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 会话 ID（业务层生成：树洞按 wish+user 维度会话） */
    private String sessionId;

    private AiScene scene;

    private AiConversationRole role;

    /** 消息内容（发送侧已脱敏；USER 记录存储用户原文） */
    private String content;

    /** 情感分数（-100~100 整数，仅 TREE_HOLE 场景 ASSISTANT 记录） */
    private Integer sentimentScore;

    /** 推荐资源 JSON（仅 TREE_HOLE 场景 ASSISTANT 记录） */
    private String resources;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

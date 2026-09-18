package com.cloudmart.pet.entity;

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
 * 宠物聊天消息。上下文窗口只取最近 N 条（PetProperties.chat.contextWindowSize），
 * 控制上下文成本；长期信息沉淀到 pet_memory 而非全量回放（原文档 §26）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_chat_message")
public class PetChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    /** USER/PET/SYSTEM */
    private String role;

    private String content;

    /** 估算 token 数（成本观测用） */
    private Integer tokenCount;

    /** 是否 AI 生成（false = 模板降级/固定行为，前端可展示"灵光一闪"标识） */
    private Boolean isAiReply;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

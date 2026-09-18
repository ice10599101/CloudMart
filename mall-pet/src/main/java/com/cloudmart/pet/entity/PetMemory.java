package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 宠物结构化记忆：聊天记录中规则抽取的关键信息（而非全量历史回放），
 * uk_pet_memory_key 按 (pet_id, memory_key) 去重，置信度低的不覆盖高置信度记忆。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_memory")
public class PetMemory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long petId;

    /** FAVORITE/HABIT/FACT */
    private String memoryType;

    /** 记忆键（如 favorite_food / owner_nickname / favorite_animal） */
    private String memoryKey;

    /** 记忆值（如"鱼"） */
    private String memoryValue;

    /** 重要度 1-5（注入 prompt 排序权重） */
    private Integer importance;

    /** 置信度 0-1（规则抽取默认 0.9；后续接 AI 抽取可调） */
    private BigDecimal confidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

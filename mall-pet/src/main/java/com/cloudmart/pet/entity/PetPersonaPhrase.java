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
 * AI 人设口头禅（F8 配置化）：每种性格一行；服务端 60 秒 TTL 缓存定时同步，
 * DB 无行时回落 Nacos 出厂默认（pet.chat.persona-phrases）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_persona_phrase")
public class PetPersonaPhrase {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 性格编码（PetPersonality 枚举） */
    private String personality;

    /** 口头禅模板（{name} 占位宠物名） */
    private String phrase;

    /** 数据库 ON UPDATE 维护，业务代码禁止写入 */
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

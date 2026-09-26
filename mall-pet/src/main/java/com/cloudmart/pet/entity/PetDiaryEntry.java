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


@Getter
@Setter
@NoArgsConstructor
@TableName("pet_diary_entry")
public class PetDiaryEntry {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 成长日记（N02）：eventId 唯一去重；关键事件不可变快照。 */
    private Long petId;
    private Long userId;
    private String eventId;
    private String eventType;
    private java.time.LocalDateTime occurredAt;
    /** 当时名字/等级/外观资源键/内容参数 */
    private String snapshot;
    /** OWNER_ONLY / PUBLIC */
    private String visibility;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

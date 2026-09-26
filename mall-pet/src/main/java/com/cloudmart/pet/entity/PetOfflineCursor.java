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

/** 离线摘要确认游标（N05）：确认只推进游标，不删除真实事件。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_offline_cursor")
public class PetOfflineCursor {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private java.time.LocalDateTime lastConfirmedAt;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

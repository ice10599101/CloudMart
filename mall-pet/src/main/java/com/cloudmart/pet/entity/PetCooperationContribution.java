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
@TableName("pet_cooperation_contribution")
public class PetCooperationContribution {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 合作贡献去重（N06）：eventId 唯一，防重复计次。 */
    private Long cooperationId;
    private Long userId;
    private String eventId;
    private java.time.LocalDate businessDate;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

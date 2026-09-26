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

/** 宠物通知偏好（B19）：免打扰/日常问候类型开关；重要业务通知不受影响。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_notify_pref")
public class PetNotifyPref {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Boolean muteDailyGreeting;
    private Boolean dailyGreetingEnabled;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

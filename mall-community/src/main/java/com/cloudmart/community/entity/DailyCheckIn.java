package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("daily_check_ins")
public class DailyCheckIn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate checkInDate;

    private Integer continuousDays;

    private Integer expReward;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

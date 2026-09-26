package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 陪伴业务日累计（B05）：同事务保存有效时长、应得积分与已发积分，
 * 公式 entitled = min(floor(acceptedSeconds / secondsPerPoint), dailyPointCap)，
 * grant = max(0, entitled - grantedPoints)。跨天按服务端区间拆分到各业务日。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_companion_daily")
public class PetCompanionDaily {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private LocalDate businessDate;

    private Integer acceptedSeconds;

    private Integer grantedPoints;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

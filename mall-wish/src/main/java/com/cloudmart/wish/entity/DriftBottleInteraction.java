package com.cloudmart.wish.entity;

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
 * 漂流瓶匿名回应：BLESS 免费 / LIGHT 扣星光 2 并点亮对方心愿；
 * uk(bottle,user,互动日) = 每瓶每用户每日 1 次。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_drift_bottle_interaction")
public class DriftBottleInteraction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 漂流瓶 ID */
    private Long bottleId;

    /** 回应发起者（捞起人） */
    private Long userId;

    /** 回应类型：BLESS 匿名祝福 / LIGHT 点亮对方心愿 */
    private String type;

    /** 对方心愿 ID（LIGHT 时 light_count +1） */
    private Long peerWishId;

    /** 互动日期（幂等键） */
    private LocalDate interactDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
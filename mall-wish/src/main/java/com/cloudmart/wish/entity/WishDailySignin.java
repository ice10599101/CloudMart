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
 * 用户每日签到记录（文档 2.6：POST /wish/my/checkin）。
 *
 * <p>与心愿打卡（{@link WishCheckin}，心愿维度）独立：本表为用户维度每日签到，
 * {@code uk_signin_daily}（user_id + signin_date）保证单用户每日仅一条。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_daily_signin")
public class WishDailySignin {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 签到日期（UTC，按日去重） */
    private LocalDate signinDate;

    /** 本次签到是否已发放星光（文档 6.1 每日签到 +5，防重复发放） */
    private Boolean starlightGranted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

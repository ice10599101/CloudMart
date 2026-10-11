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
 * 星光日转出额度（R06）：(user_id, business_date) 唯一权威行，
 * 转赠与额度占用同事务条件更新 used_amount + amount <= 200（原子，不超用）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("starlight_daily_out_quota")
public class StarlightDailyOutQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate businessDate;

    private Integer usedAmount;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.cloudmart.community.entity;

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
 * R21 社区经验日配额占用（community_exp_daily_quota）。
 * 每用户每业务日一行；发放在事务内对该行 FOR UPDATE 加锁后累加 granted，
 * 保证并发请求合计不突破日上限（200）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("community_exp_daily_quota")
public class ExpDailyQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate businessDate;

    /** 当日已占用经验额度（行锁内原子累加） */
    private Integer granted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

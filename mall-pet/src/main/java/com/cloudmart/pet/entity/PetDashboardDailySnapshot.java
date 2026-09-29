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
 * 看板每日快照（P2-3）：按 (stat_date, metric_key) 唯一，调度器小时级 upsert 当日值，
 * 历史日冻结——看板趋势读快照 + 当日实时合并，不再每次全表聚合。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_dashboard_daily_snapshot")
public class PetDashboardDailySnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 统计日（UTC 自然日） */
    private LocalDate statDate;

    /** 指标键：new_pets/active_pets/activities/wall_messages/visits/battles */
    private String metricKey;

    private Long metricValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 数据库 ON UPDATE 维护，业务代码禁止写入 */
    private LocalDateTime updatedAt;
}

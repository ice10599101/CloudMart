package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 软件日聚合（SF06）：由 rebuild 从事实源全量精确重算；唯一键 (stat_date, software_id)
 * 保证重复执行不累计翻倍。时间口径 Asia/Shanghai 自然日。
 */
@Getter
@Setter
@TableName("software_daily_stat")
public class SoftwareDailyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Long softwareId;

    /** 获取链接次数（access_daily 行数口径；站内去重获取，非外部下载量） */
    private Integer resolveCount;

    /** 获取链接人数（当日去重用户） */
    private Integer resolveUserCount;

    private Integer favoriteAdd;

    private Integer favoriteRemove;

    private Integer subscribeAdd;

    private Integer subscribeRemove;

    private LocalDateTime updatedAt;
}

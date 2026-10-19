package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收藏/订阅增减事实事件（SF06）：取消后关系行消失，必须事件化才能分别记录
 * 新增与取消；resolve 类指标事实源为 software_link_access_daily（禁止另建）。
 */
@Getter
@Setter
@TableName("software_stat_event")
public class SoftwareStatEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long userId;

    /** FAVORITE_ADD/FAVORITE_REMOVE/SUBSCRIBE_ADD/SUBSCRIBE_REMOVE */
    private String kind;

    /** 事件日期（Asia/Shanghai 切日） */
    private LocalDate eventDate;

    private LocalDateTime createdAt;
}

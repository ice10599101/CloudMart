package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页固定区块（UX01）：运营可组织内容，前端不写死推荐位/场景/顺序。
 * kind/data_source 白名单（CHECK + 应用层双校验）；保存走事务+状态检查+幂等键，
 * 不引入配置 expectedVersion 或发布版本号。
 */
@Data
@TableName("software_home_section")
public class SoftwareHomeSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定槽位键（前端降级定位；唯一） */
    private String slotKey;

    private String title;

    private String subtitle;

    /** SPOTLIGHT/SCENARIOS/SOFTWARE_GRID/RANKING_LIST/UPDATE_FEED/GUIDE_LIST */
    private String kind;

    private Boolean enabled;

    private Integer displayOrder;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 平台范围 CSV（WEB/APP/TARO；NULL=全部端） */
    private String platformScope;

    /** 条目上限（1~20） */
    private Integer limitCount;

    /** 最少条目（不足整块隐藏；0=不启用） */
    private Integer minItems;

    /** MANUAL/FEATURED/LATEST/RANKING/TOPIC/GUIDE */
    private String dataSource;

    /** 受控筛选 preset（仅 Explore 白名单参数） */
    private String filterJson;

    /** HIDE(内容不足隐藏)/EMPTY(显式空态) */
    private String fallbackPolicy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}

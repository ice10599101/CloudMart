package com.cloudmart.software.dto.software;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** UX01：首页区块保存（slotKey 唯一；kind/data_source 白名单；时间 start<end） */
@Data
public class AdminHomeSectionSaveRequest {

    private String slotKey;

    private String title;

    private String subtitle;

    /** SPOTLIGHT/SCENARIOS/SOFTWARE_GRID/RANKING_LIST/UPDATE_FEED/GUIDE_LIST */
    private String kind;

    private Boolean enabled;

    private Integer displayOrder;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 平台范围（WEB/APP/TARO；空=全部端） */
    private List<String> platformScope;

    /** 条目上限（1~20） */
    private Integer limitCount;

    /** 最少条目（0~limit；不足整块隐藏） */
    private Integer minItems;

    /** MANUAL/FEATURED/LATEST/RANKING/TOPIC/GUIDE */
    private String dataSource;

    /** 内容不足策略：HIDE/EMPTY */
    private String fallbackPolicy;

    /** MANUAL 条目全量（与区块同事务替换） */
    private List<HomeItemInput> items;

    @Data
    public static class HomeItemInput {
        /** SOFTWARE/TOPIC/GUIDE/FILTER */
        private String targetType;
        private Long targetId;
        /** 受控筛选 preset（FILTER 用；仅 Explore 白名单参数键值） */
        private String filterPreset;
        private String headline;
        private String editorNote;
        private Long coverAssetId;
        private Integer displayOrder;
    }
}

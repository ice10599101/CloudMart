package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 管理端专题新增/更新（方案 6.11/7.5 P1）：专题与软件排序项整体维护；
 * items 全量替换（携带 softwareId + sortOrder）。
 */
@Getter
@Setter
public class AdminTopicSaveRequest {

    /** 专题标识（URL 用，小写字母/数字/连字符，唯一） */
    private String slug;

    /** 标题（1~80 字） */
    private String title;

    /** 运营说明（≤500 字） */
    private String summary;

    /** 封面素材（mall-file 资产ID，可空） */
    private Long coverAssetId;

    /** DRAFT/PUBLISHED/DISABLED（可空=DRAFT） */
    private String status;

    /** 排序（可空=0） */
    private Integer sortOrder;

    /** SF05：专题介绍 */
    private String description;

    /** SF05：定时上线时间（UTC；NULL=发布即生效） */
    private java.time.LocalDateTime scheduledStartAt;

    /** SF05：定时下线时间（UTC；NULL=不自动下线） */
    private java.time.LocalDateTime scheduledEndAt;

    /** 软件清单（全量替换；≤100 项，顺序即展示顺序） */
    private List<Item> items;

    /** 清单项：softwareId + 可选 sortOrder（缺省按数组顺序） */
    @Getter
    @Setter
    public static class Item {

        private Long softwareId;

        private Integer sortOrder;
    }
}

package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 快照完整输入（方案 7.3 PUT save / 7.4 示例）：白名单字段、全量替换子项；
 * releaseKey/sourceKey 为 null 表示新建（服务端生成 UUID），携带键则按键更新；
 * ID 类字段前端一律字符串（全局序列化），此处统一 Long 接收。
 */
@Getter
@Setter
public class SoftwareSnapshotInput {

    /** 软件名（提交时 2~80 字；草稿可空） */
    private String name;

    /** 一句话简介（选填：提交可空；填则 10~160 字） */
    private String summary;

    private Long categoryId;

    private Long iconAssetId;

    /** 富文本介绍（服务端消毒；≤50000 字符） */
    private String descriptionHtml;

    private String licenseType;

    private String developerName;

    private String officialUrl;

    private String attributionType;

    private String attributionNote;

    private String systemRequirements;

    /** 语言代码数组（JSON 序列化存储） */
    private List<String> languages;

    private String accessMode;

    /** 等级模式最低社区等级（仅 COMMUNITY_LEVEL） */
    private Integer minCommunityLevel;

    /** 付款模式联系入口（仅 ADMIN_PAID） */
    private Long accessContactId;

    private String accessNote;

    /** 标签（≤5，须为启用标签） */
    private List<Long> tagIds;

    /** 截图（≤8：{assetId, altText}） */
    private List<MediaInput> screenshots;

    /** 版本列表（≤50；releaseKey null=新建） */
    private List<ReleaseInput> releases;

    @Getter
    @Setter
    public static class MediaInput {
        private Long assetId;
        private String altText;
        private Integer displayOrder;
    }

    @Getter
    @Setter
    public static class ReleaseInput {
        /** 跨快照稳定版本键（null=新建） */
        private String releaseKey;
        private String versionName;
        private String channel;
        private Boolean recommended;
        private LocalDate releaseDate;
        private String changelog;
        private Integer displayOrder;
        private List<SourceInput> sources;
    }

    @Getter
    @Setter
    public static class SourceInput {
        /** 跨快照稳定来源键（null=新建） */
        private String sourceKey;
        private String sourceType;
        private String provider;
        private String platform;
        private String arch;
        private String label;
        private String url;
        private String extractionCode;
        private String archivePassword;
        private String sizeLabel;
        private String claimedSha256;
        private String note;
        private Integer displayOrder;
    }
}

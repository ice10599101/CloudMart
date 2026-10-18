package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.enums.SoftwareLicenseType;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 内容快照（方案 6.3）：名称/正文/查看条件整体克隆、冻结、审核、切换；
 * 已审快照不可原地编辑；复制快照创建新 revisionId。
 */
@Getter
@Setter
@TableName("software_revision")
public class SoftwareRevision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    /** 编辑人（前台会员 ID） */
    private Long editorId;

    private SoftwareReviewStatus reviewStatus;

    /** 软件名（草稿可空，提交时 2~80 字） */
    private String name;

    /** 一句话简介（选填：提交可空；填则 10~160 字） */
    private String summary;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;

    /** 图标素材（mall-file 资产 ID） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long iconAssetId;

    /** 服务端消毒后的富文本介绍 */
    private String descriptionHtml;

    /** 消毒后 HTML 提取的纯文本（搜索/审核用） */
    private String descriptionText;

    /** 软件开发者名称（区别于本站分享者） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String developerName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String officialUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private SoftwareLicenseType licenseType;

    /** 来源声明：ORIGINAL/AUTHORIZED/OFFICIAL_PUBLIC */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String attributionType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String attributionNote;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String systemRequirements;

    /** 语言代码 JSON 数组 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String languages;

    private SoftwareAccessMode accessMode;

    /** 等级模式最低社区等级（须为现有启用等级；仅 COMMUNITY_LEVEL 非空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer minCommunityLevel;

    /** 付款模式管理员联系入口（仅 ADMIN_PAID 非空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long accessContactId;

    /** 查看条件说明（不存源链接/密码/支付凭证） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String accessNote;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    /** 审核管理员 ID（mall-admin 管理员） */
    private Long reviewerId;

    private String reviewReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

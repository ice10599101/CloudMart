package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 软件举报创建（方案 7.3/6.12）：举报软件本体或该软件下的可见评论；
 * 处理动作（封禁/隐藏评论）由管理员显式选择，与单纯失效链接反馈分开。
 */
@Getter
@Setter
public class SoftwareReportCreateRequest {

    /** SOFTWARE / SOFTWARE_COMMENT */
    private String targetType;

    /** 评论举报必填（须为该软件下的可见评论）；软件举报忽略（服务端置为软件本体） */
    private Long targetId;

    /** 举报理由（1~50 字，如 侵权抄袭/恶意内容/违规评论） */
    private String reason;

    /** 补充说明（≤500 字） */
    private String description;

    /** 证据素材 ID（可选，≤6 个；仅本人文件资产，best-effort 绑定） */
    private List<Long> evidenceAssetIds;
}

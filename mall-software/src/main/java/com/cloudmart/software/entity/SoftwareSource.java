package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SoftwareArch;
import com.cloudmart.software.enums.SoftwarePlatform;
import com.cloudmart.software.enums.SoftwareProvider;
import com.cloudmart.software.enums.SoftwareSourceType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 下载来源（方案 6.5）：NETDISK/MAGNET/TORRENT_URL 外部链接；
 * url/提取码/解压密码仅授权响应可返回，禁止出现在公开详情/列表/日志。
 */
@Getter
@Setter
@TableName("software_source")
public class SoftwareSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long releaseId;

    /** 跨快照稳定来源键（source_state/统计/反馈按此关联） */
    private String sourceKey;

    private SoftwareSourceType sourceType;

    private SoftwareProvider provider;

    private SoftwarePlatform platform;

    private SoftwareArch arch;

    /** 来源展示名（如"Windows 64 位稳定版"） */
    private String label;

    /** 校验后的完整链接（服务端校验；仅 resolve/授权响应可返回） */
    private String url;

    /** 规范化 URL 哈希（服务端 SHA-256） */
    private String urlHash;

    /** 内容指纹（规范化 URL+提取码+解压密码；核实结论按指纹匹配） */
    private String contentFingerprint;

    private String extractionCode;

    private String archivePassword;

    /** 文件大小描述（发布者声明，未经本站验证） */
    private String sizeLabel;

    /** 发布者提供的 SHA-256（页面标注"发布者提供"） */
    private String claimedSha256;

    /** 来源说明（不含秘密信息） */
    private String note;

    /** 同版本内排序 */
    private Integer displayOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

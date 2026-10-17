package com.cloudmart.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件资产台账（FILE-01）：所有上传登记归属与内容指纹；删除按 fileId 校验。
 */
@Data
@TableName("file_asset")
public class FileAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户ID；null=平台/存量无主（LEGACY_UNCLAIMED 仅管理员可处置） */
    private Long ownerId;

    /** ADMIN-04：归属域 USER/ADMIN（管理员运营资产不进入用户资产空间） */
    private String ownerType;

    /** ADMIN-04：上传操作者（管理员域=管理员账号；用户域=owner_id） */
    private Long createdBy;

    /** ADMIN-04：业务用途（后台上传必填，如 GIFT_IMAGE/WISH_BGM/ADMIN_ASSET） */
    private String purpose;

    private String originalName;

    /** 存储键（相对存储根路径，唯一） */
    private String storageKey;

    /** 实际嗅探的 MIME（魔数判定，非客户端声明） */
    private String mime;

    private Long sizeBytes;

    private String sha256;

    /** PUBLIC / PRIVATE */
    private String visibility;

    /** READY / DELETED / LEGACY_UNCLAIMED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@TableName("pet_album_asset")
public class PetAlbumAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 相册资源（N02/R04）：mall-file 资源引用，上传者/归属校验，审核状态与绑定状态。 */
    private Long userId;
    private Long petId;
    private Long diaryEntryId;
    private String fileId;
    private String auditStatus;
    /** R04 远程文件引用绑定状态：BINDING/BOUND/FAILED */
    private String bindStatus;

    /** PET-13/T32：绑定已尝试次数（上传 1 次 + 自动/手动重试） */
    @TableField("bind_attempts")
    private Integer bindAttempts;

    /** PET-13：下次自动重试时间(UTC)；NULL 表示无待重试 */
    @TableField("next_bind_retry_at")
    private LocalDateTime nextBindRetryAt;

    /** PET-13：最近一次绑定失败原因（截断 255 字符） */
    @TableField("last_bind_error")
    private String lastBindError;
    /** 用户说明（≤200 字，仅 owner 本人可改） */
    private String caption;
    /** OWNER_ONLY / PUBLIC：访客仅见 PUBLIC+APPROVED+BOUND */
    private String visibility;
    /** 乐观版本（caption/visibility PATCH CAS） */
    private Integer version;
    /** 审核处理人（adminUserId） */
    private Long reviewerId;
    /** 审核理由（驳回必填） */
    private String reviewReason;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

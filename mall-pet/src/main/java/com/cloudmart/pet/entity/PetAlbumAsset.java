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

/** 相册资源（N02）：mall-file 资源引用，上传者/归属校验，审核状态。 */
    private Long userId;
    private Long petId;
    private Long diaryEntryId;
    private String fileId;
    private String auditStatus;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

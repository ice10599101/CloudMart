package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 快照素材关系（方案 6.7）：图标/截图/正文媒体；bizType=SOFTWARE_REVISION 引用绑定 */
@Getter
@Setter
@TableName("software_revision_media")
public class SoftwareRevisionMedia {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long revisionId;

    /** 素材资产 ID（mall-file，逻辑引用；归属校验在文件服务） */
    private Long assetId;

    /** SCREENSHOT/BODY */
    private String kind;

    private Integer displayOrder;

    /** 无障碍替代文本 */
    private String altText;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

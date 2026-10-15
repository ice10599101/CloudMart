package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 软件专题合集（方案 6.11，P1）：公开查询只显示 PUBLISHED，清单实时过滤不可见软件 */
@Getter
@Setter
@TableName("software_topic")
public class SoftwareTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String slug;

    private String title;

    private String summary;

    private Long coverAssetId;

    /** DRAFT/PUBLISHED/DISABLED */
    private String status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

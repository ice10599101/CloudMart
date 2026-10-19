package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 软件同类关系（UX03）：人工维护的"同类用途/同平台替代"关系，
 * 不上黑盒推荐系统。禁止自关联与重复（唯一键兜底）；
 * 公开聚合时实时过滤不可见目标（下架/封禁不泄露）。
 */
@Data
@TableName("software_relation")
public class SoftwareRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long relatedId;

    /** SIMILAR_USE(同类用途) / ALTERNATIVE_PLATFORM(同平台替代) */
    private String relationType;

    private String reason;

    private Integer displayOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 软件教程元数据（UX04）：正文与审核真源在 mall-community（复用 Post），
 * 软件域只存定位元数据；查询时实时校验文章公开状态，文章撤回立即隐藏。
 */
@Data
@TableName("software_guide")
public class SoftwareGuide {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** INSTALL_GUIDE/WORKFLOW/COMPARISON/TROUBLESHOOTING */
    private String guideType;

    private String title;

    private String summary;

    /** 社区帖子 ID（正文与审核真源） */
    private Long articleId;

    private Long coverAssetId;

    /** 关联软件 ID JSON 数组（≤3；至少关联一个软件或专题） */
    private String relatedSoftwareIds;

    private Long relatedTopicId;

    /** 适用平台 CSV */
    private String platformScope;

    private String appliesToVersion;

    /** 内容检查日期（无真实材料不启用教程区块） */
    private LocalDate checkedAt;

    private String checkNote;

    private Boolean enabled;

    private Integer displayOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}

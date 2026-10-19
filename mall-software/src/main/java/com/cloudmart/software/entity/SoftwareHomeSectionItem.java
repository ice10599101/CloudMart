package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 首页区块条目（UX01）：运营推荐文案与作者原始内容分离。 */
@Data
@TableName("software_home_section_item")
public class SoftwareHomeSectionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sectionId;

    /** SOFTWARE/TOPIC/GUIDE/FILTER */
    private String targetType;

    /** 目标 ID（SOFTWARE/TOPIC/GUIDE 必填；FILTER 为空） */
    private Long targetId;

    /** 受控筛选 preset（FILTER 类型用） */
    private String filterPreset;

    private String headline;

    private String editorNote;

    private Long coverAssetId;

    private Integer displayOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

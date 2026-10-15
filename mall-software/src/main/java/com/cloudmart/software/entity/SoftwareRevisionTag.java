package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 快照标签关系（方案 6.7）：纯关系表，复合主键 (revision_id, tag_id) */
@Getter
@Setter
@TableName("software_revision_tag")
public class SoftwareRevisionTag {

    private Long revisionId;

    private Long tagId;
}

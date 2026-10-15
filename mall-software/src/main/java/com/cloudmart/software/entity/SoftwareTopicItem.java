package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 专题软件清单项（方案 6.11，P1）：UNIQUE(topic_id,software_id)，整体替换维护 */
@Getter
@Setter
@TableName("software_topic_item")
public class SoftwareTopicItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long topicId;

    private Long softwareId;

    private Integer sortOrder;
}

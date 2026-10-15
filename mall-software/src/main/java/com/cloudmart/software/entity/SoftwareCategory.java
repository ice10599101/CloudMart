package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 运营分类（方案 6.7）：单层；禁用后不得新选，已发布项保留分类名 */
@Getter
@Setter
@TableName("software_category")
public class SoftwareCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String slug;

    private String icon;

    private Integer sortOrder;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

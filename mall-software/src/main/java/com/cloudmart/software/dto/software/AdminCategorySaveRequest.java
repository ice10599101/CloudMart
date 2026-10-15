package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端分类新增/更新（方案 7.5/10.5）：启用、禁用、排序；
 * 禁用后不得新选，已发布项保留分类名；删除即禁用，不做物理删除。
 */
@Getter
@Setter
public class AdminCategorySaveRequest {

    /** 分类名（1~40 字） */
    private String name;

    /** 分类标识（URL 用，小写字母/数字/连字符，唯一） */
    private String slug;

    /** 图标标识（可空，≤100 字符） */
    private String icon;

    /** 排序（空=0） */
    private Integer sortOrder;

    /** 启用（空=true；false=禁用） */
    private Boolean enabled;
}

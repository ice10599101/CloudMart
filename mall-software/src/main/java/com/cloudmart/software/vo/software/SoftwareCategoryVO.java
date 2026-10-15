package com.cloudmart.software.vo.software;

/** 分类（方案 7.2）：禁用分类不作为新筛选项，已发布项保留分类名 */
public record SoftwareCategoryVO(
        Long id,
        String name,
        String slug,
        String icon,
        Integer sortOrder,
        Boolean enabled) {
}

package com.cloudmart.wish.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新心愿分类请求 DTO。
 *
 * <p>对应 API: PUT /wish/admin/categories/{id}（管理后台）</p>
 */
public record UpdateCategoryRequest(

        @Size(max = 60, message = "分类名称不能超过60字符")
        String name,

        Integer sort,

        String icon
) {}

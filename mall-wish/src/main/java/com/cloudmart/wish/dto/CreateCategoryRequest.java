package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建心愿分类请求 DTO。
 *
 * <p>对应 API: POST /wish/admin/categories（管理后台）</p>
 */
public record CreateCategoryRequest(

        @NotBlank(message = "分类编码不能为空")
        @Size(max = 30, message = "分类编码不能超过30字符")
        String code,

        @NotBlank(message = "分类名称不能为空")
        @Size(max = 60, message = "分类名称不能超过60字符")
        String name,

        Integer sort,

        String icon
) {}

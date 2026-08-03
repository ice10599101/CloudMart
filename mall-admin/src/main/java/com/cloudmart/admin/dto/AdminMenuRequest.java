package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminMenuRequest(
    @NotBlank String menuName,
    Long parentId,
    @NotNull Integer orderNum,
    String path,
    String component,
    String query,
    String routeName,
    Integer isFrame,
    Integer isCache,
    @NotBlank String menuType,
    Integer visible,
    Integer status,
    String perms,
    String icon,
    String remark
) {}

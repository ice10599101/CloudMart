package com.cloudmart.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminMenuResponse(
    Long id,
    String menuName,
    Long parentId,
    Integer orderNum,
    String path,
    String component,
    String query,
    String routeName,
    Integer isFrame,
    Integer isCache,
    String menuType,
    Integer visible,
    Integer status,
    String perms,
    String icon,
    String remark,
    LocalDateTime createdAt,
    List<AdminMenuResponse> children
) {}

package com.cloudmart.admin.dto.feign;

/**
 * 通用计数响应，用于 productCount / memberCount 等返回 {"count": N} 的接口
 */
public record CountResponse(
    Long count
) {}

package com.cloudmart.community.dto;

public record AdminPostQueryRequest(
    String keyword,
    Integer status,
    Long userId,
    Integer page,
    Integer pageSize
) {}

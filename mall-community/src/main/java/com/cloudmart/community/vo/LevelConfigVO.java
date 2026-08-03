package com.cloudmart.community.vo;

public record LevelConfigVO(
    Long id,
    Integer level,
    String title,
    Integer minExp,
    String icon,
    String benefits,
    Integer status
) {}

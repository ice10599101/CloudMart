package com.cloudmart.wish.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端徽章视图（含下架状态与原始 condition JSON，供编辑器回显）。
 */
@Data
public class AdminBadgeVO {

    private Long id;

    private String code;

    private String name;

    private String icon;

    private String rarity;

    private Boolean isActive;

    /** 原始 condition JSON（编辑器回显/语法高亮） */
    private String condition;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

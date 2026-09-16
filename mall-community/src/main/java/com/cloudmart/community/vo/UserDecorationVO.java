package com.cloudmart.community.vo;

/**
 * 用户头像装饰信息（头像框 / 等级 / 徽章数）。
 *
 * <p>用于全站任意位置展示用户头像处的统一装饰：
 * 头像框（avatarFrame）、等级（level + levelTitle + levelIcon）、
 * 徽章角标（badgeCount）。贵宾标识（Lv6+）与官方认证（Lv7+）由前端依据 level 推导。</p>
 */
public record UserDecorationVO(
    Long userId,
    Integer level,
    String levelTitle,
    String levelIcon,
    String avatarFrame,
    Long badgeCount
) {}
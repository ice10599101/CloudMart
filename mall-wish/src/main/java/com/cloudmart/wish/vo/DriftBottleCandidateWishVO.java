package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 可关联心愿候选（投瓶下拉选择）：我最近发布的可关联心愿。
 *
 * @param wishId 心愿 ID
 * @param title  心愿标题
 * @param tags   心愿标签
 */
@Schema(description = "可关联心愿候选")
public record DriftBottleCandidateWishVO(
        Long wishId,
        String title,
        List<String> tags
) {
}
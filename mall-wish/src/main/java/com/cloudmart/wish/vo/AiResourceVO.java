package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 推荐资源 VO（文档 2.11 树洞回复 resources 定义）。
 *
 * @param type  资源类型：ARTICLE / HOTLINE
 * @param title 资源名称
 * @param url   资源链接
 */
@Schema(description = "AI 推荐资源")
public record AiResourceVO(
        @Schema(description = "资源类型", example = "HOTLINE") String type,
        @Schema(description = "资源名称", example = "全国心理援助热线") String title,
        @Schema(description = "资源链接", example = "tel:12356") String url
) {
}

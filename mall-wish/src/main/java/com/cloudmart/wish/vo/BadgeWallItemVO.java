package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 徽章墙条目 VO（文档 2.6 GET /wish/my/badges 聚合视图）。
 *
 * <p>覆盖全部徽章定义：已获得返回 earnedAt，未获得返回 condition + progress
 * （前端灰色锁定态 + hover 展示获取方式与进度，Sprint 1.4 验收语义）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "徽章墙条目（已获得 + 未获得聚合）")
public class BadgeWallItemVO extends BadgeDefinitionVO {

    @Schema(description = "是否已获得", example = "true")
    private Boolean earned;

    @Schema(description = "获得时间（未获得为 null）")
    private LocalDateTime earnedAt;

    @Schema(description = "达成进度（未获得时前端展示进度条；已获得 current=threshold）")
    private ProgressVO progress;

    /**
     * 达成进度展示结构。
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "徽章达成进度")
    public static class ProgressVO {

        @Schema(description = "当前累计值", example = "3")
        private Integer current;

        @Schema(description = "达标阈值", example = "100")
        private Integer threshold;

        @Schema(description = "进度百分比（0-100，向上取整封顶）", example = "3")
        private Integer percentage;
    }
}

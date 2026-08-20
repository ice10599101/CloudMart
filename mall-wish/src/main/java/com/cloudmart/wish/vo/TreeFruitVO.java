package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 世界树果实 VO（对应文档 2.5 GET /wish/tree/fruits 数组元素）。
 *
 * <p>安全约束（文档 Sprint 2.1 安全验收）：果实数据不含用户敏感信息
 * （手机号/邮箱等），仅含展示必需字段；作者昵称经批量 Feign 解析，
 * mall-user 不可用时 Fail-Open 降级为占位昵称「心愿旅人」。</p>
 *
 * @param id             心愿 ID（即果实 ID，1:1）
 * @param title          心愿标题
 * @param fruitType      果实类型（GLOW 微光/RESONANCE 共鸣/BLOOM 绽放/SPARK 星火）
 * @param authorNickname 作者昵称（Feign 失败时降级占位）
 * @param lightCount     累计点亮数
 * @param position       球面坐标（弧度制）
 */
@Schema(name = "TreeFruitVO", description = "世界树果实（按视口分页返回）")
public record TreeFruitVO(

        @Schema(description = "心愿 ID（果实 ID）", example = "1948000000000000001")
        Long id,

        @Schema(description = "心愿标题", example = "考上理想的研究生")
        String title,

        @Schema(description = "果实类型（GLOW/RESONANCE/BLOOM/SPARK）", example = "GLOW")
        FruitType fruitType,

        @Schema(description = "作者昵称（Feign 降级时为「心愿旅人」）", example = "追光少女")
        String authorNickname,

        @Schema(description = "累计点亮数", example = "12")
        Integer lightCount,

        @Schema(description = "球面坐标（弧度制）")
        TreePositionVO position
) {
}

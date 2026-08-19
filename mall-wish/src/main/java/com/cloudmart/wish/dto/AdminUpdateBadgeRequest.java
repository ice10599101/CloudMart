package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端编辑徽章请求。
 *
 * <p>code 不可修改（业务键，已被 wish_user_badge.badge_id 关联与外部引用），
 * 不在本请求中出现；condition 结构校验同 {@link AdminCreateBadgeRequest}。</p>
 */
@Data
public class AdminUpdateBadgeRequest {

    @NotBlank(message = "徽章名称不能为空")
    @Size(max = 60, message = "徽章名称长度不能超过 60")
    private String name;

    @Size(max = 255, message = "图标 URL 长度不能超过 255")
    private String icon;

    @NotBlank(message = "稀有度不能为空")
    @Pattern(regexp = "COMMON|RARE|EPIC|LEGENDARY", message = "稀有度必须为 COMMON/RARE/EPIC/LEGENDARY")
    private String rarity;

    @NotBlank(message = "condition 不能为空")
    private String condition;
}

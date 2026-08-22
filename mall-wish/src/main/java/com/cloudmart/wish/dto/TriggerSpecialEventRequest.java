package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端触发全站特殊事件请求（Sprint 2.2 特殊事件触发台）。
 *
 * <p>eventCode 须为 wish_env_config 中已启用的 SPECIAL_EVENT 类环境
 * （如 METEOR_SHOWER/AURORA/STAR_NIGHT），Service 层校验；durationMinutes
 * 为空表示持续至管理员手动结束。</p>
 */
@Data
public class TriggerSpecialEventRequest {

    @NotBlank(message = "事件代码不能为空")
    @Size(max = 48, message = "事件代码长度不能超过 48")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "事件代码为大写字母开头的字母/数字/下划线")
    private String eventCode;

    @Size(max = 64, message = "事件标题长度不能超过 64")
    private String title;

    @Size(max = 255, message = "事件描述长度不能超过 255")
    private String description;

    /** 持续分钟数；null=持续至手动结束 */
    @Positive(message = "持续分钟数必须为正整数")
    private Integer durationMinutes;
}

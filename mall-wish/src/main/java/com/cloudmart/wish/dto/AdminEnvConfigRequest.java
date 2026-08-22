package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端环境配置新增/编辑请求（Sprint 2.2 环境配置管理，表化配置）。
 *
 * <p>visual 为 JSON 字符串（编辑器提交），结构校验在 Service 层执行
 * （Bean Validation 无法表达 JSON 结构规则）；编辑时 envCode 不可改
 * （code 是天气/季节/事件链路的关联键）。</p>
 */
@Data
public class AdminEnvConfigRequest {

    @NotBlank(message = "环境代码不能为空")
    @Size(max = 48, message = "环境代码长度不能超过 48")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "环境代码为大写字母开头的字母/数字/下划线")
    private String envCode;

    @NotBlank(message = "环境分类不能为空")
    @Pattern(regexp = "WEATHER|SEASON|TIME|SPECIAL_EVENT",
            message = "环境分类必须为 WEATHER/SEASON/TIME/SPECIAL_EVENT")
    private String category;

    @NotBlank(message = "环境名称不能为空")
    @Size(max = 64, message = "环境名称长度不能超过 64")
    private String name;

    @Size(max = 255, message = "环境描述长度不能超过 255")
    private String description;

    @NotNull(message = "渲染优先级不能为空")
    private Integer priority;

    /** 渲染视觉参数 JSON（crownColor/skyColor/particle/lightCoreColor 等） */
    private String visual;

    /** 是否启用（默认 true；下架后读取方过滤） */
    private Boolean active;
}

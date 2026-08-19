package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端新增徽章请求（文档 33.4.7 徽章管理）。
 *
 * <p>condition 为 JSON 字符串（编辑器提交），结构校验在 Service 层经
 * {@code BadgeConditionParser.validate} 执行（Bean Validation 无法表达
 * JSON 结构规则，错误信息需可读反馈给编辑器）。</p>
 */
@Data
public class AdminCreateBadgeRequest {

    @NotBlank(message = "徽章编码不能为空")
    @Size(max = 30, message = "徽章编码长度不能超过 30")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "编码为大写字母开头的字母/数字/下划线")
    private String code;

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

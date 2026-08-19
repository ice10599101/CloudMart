package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 提交同意/撤回请求（文档 1.2 节 ⑳）。
 *
 * @param consentType     同意类型
 * @param version         协议版本号
 * @param action          动作（默认 GRANT）
 * @param consentTextHash 同意时协议文本 SHA-256 哈希（64 位十六进制，可空：
 *                        协议文本管理模块上线前由服务端按 type+version 生成确定性哈希）
 */
@Schema(description = "提交同意/撤回请求")
public record GrantConsentRequest(
        @Schema(description = "同意类型", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "同意类型不能为空")
        ConsentType consentType,

        @Schema(description = "协议版本号", example = "v1.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "协议版本号不能为空")
        @Size(max = 20, message = "协议版本号最长 20 字符")
        String version,

        @Schema(description = "动作：GRANT 同意 / WITHDRAW 撤回")
        ConsentAction action,

        @Schema(description = "协议文本 SHA-256 哈希（64 位十六进制）")
        @Pattern(regexp = "^$|^[a-fA-F0-9]{64}$", message = "协议文本哈希格式不正确")
        String consentTextHash
) {
    public ConsentAction safeAction() {
        return action == null ? ConsentAction.GRANT : action;
    }
}

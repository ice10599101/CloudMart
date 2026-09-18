package com.cloudmart.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建投票请求（编辑器附件，V10）。
 *
 * <p>{@code id} 为客户端生成 UUID，后端以该 ID 幂等落库（已存在且创建者一致则直接返回）。</p>
 *
 * @param id         客户端 UUID
 * @param targetType 宿主内容类型: POST/WISH/CAPSULE/LETTER
 * @param targetId   宿主内容 ID
 * @param question   投票问题
 * @param multiple   是否多选
 * @param options    选项（2-10 个）
 */
@Schema(description = "创建投票请求")
public record CreatePollRequest(
        @Schema(description = "客户端 UUID（幂等主键）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "投票 ID 不能为空")
        @Size(max = 36, message = "投票 ID 长度非法")
        String id,

        @Schema(description = "宿主内容类型", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"POST", "WISH", "CAPSULE", "LETTER"})
        @NotBlank(message = "宿主内容类型不能为空")
        @Pattern(regexp = "POST|WISH|CAPSULE|LETTER", message = "宿主内容类型非法")
        String targetType,

        @Schema(description = "宿主内容 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "宿主内容 ID 不能为空")
        @Size(max = 64, message = "宿主内容 ID 长度非法")
        String targetId,

        @Schema(description = "投票问题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "投票问题不能为空")
        @Size(max = 200, message = "问题不能超过 200 字符")
        String question,

        @Schema(description = "是否多选")
        Boolean multiple,

        @Schema(description = "选项（2-10 个）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "选项不能为空")
        @Size(min = 2, max = 10, message = "选项数量须为 2-10 个")
        List<@NotBlank(message = "选项文本不能为空") @Size(max = 200, message = "选项不能超过 200 字符") String> options
) {
}

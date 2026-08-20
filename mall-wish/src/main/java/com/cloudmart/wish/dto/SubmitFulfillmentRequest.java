package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 提交还愿请求 DTO。
 *
 * <p>对应 API: POST /wish/wishes/{id}/fulfillment（文档 2.4 节）。</p>
 */
public record SubmitFulfillmentRequest(

        @NotBlank(message = "还愿故事不能为空")
        @Size(max = 5000, message = "还愿故事不能超过5000字符")
        String story,

        @Size(max = 9, message = "完成照片/视频最多9个")
        List<String> mediaUrls,

        @Size(max = 1000, message = "感悟不能超过1000字符")
        String feeling
) {
}

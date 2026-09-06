package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建评论请求。
 * postId 由路径 /posts/{postId}/comments 提供并经 Service 校验，
 * 请求体不再重复携带，避免前后端契约不一致。
 */
public record CreateCommentRequest(
    Long parentId,
    Long replyToUserId,
    @NotBlank @Size(max = 1000) String content
) {}

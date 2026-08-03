package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
    @NotNull Long postId,
    Long parentId,
    Long replyToUserId,
    @NotBlank @Size(max = 1000) String content
) {}

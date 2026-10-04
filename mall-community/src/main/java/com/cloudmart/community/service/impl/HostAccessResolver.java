package com.cloudmart.community.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.repository.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 编辑器附件宿主授权解析器（T21）：投票/问卷/富文本附件继承宿主帖子的
 * 所有权与可见性约束——只知道 pollId/surveyId 不能绕过私密宿主。
 *
 * <p>约束域：当前仅支持 POST 宿主（社区富文本编辑器唯一宿主域）；
 * WISH/CAPSULE/LETTER 等跨域宿主未接入可见性解析，明确拒绝而非默认公开
 * （不猜测他域可见性语义）。未绑定宿主的独立附件不受宿主约束。</p>
 */
@Component
@RequiredArgsConstructor
public class HostAccessResolver {

    private final PostMapper postMapper;

    /** 创建/挂载附件：当前用户必须可编辑宿主（宿主作者本人）。 */
    public void requireHostEditable(String targetType, String targetId, Long userId) {
        Post host = resolveHostOrNull(targetType, targetId);
        if (host == null) {
            return;
        }
        if (!host.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "仅宿主作者可挂载该附件");
        }
    }

    /** 读取/作答：宿主公开可见（已发布且审核通过）或为宿主作者本人。 */
    public void requireHostReadable(String targetType, String targetId, Long viewerId) {
        Post host = resolveHostOrNull(targetType, targetId);
        if (host == null) {
            return;
        }
        boolean publiclyVisible = host.getStatus() != null && host.getStatus() == 1
                && host.getReviewStatus() != null && host.getReviewStatus() == 1;
        if (!publiclyVisible && !host.getUserId().equals(viewerId)) {
            // 与帖子详情同语义：按不存在处理，不泄露私密内容存在性
            throw new BusinessException("RESOURCE_NOT_VISIBLE", "内容不可见");
        }
    }

    /** @return 宿主帖子；未绑定宿主（targetId 空）返回 null；非 POST 宿主域明确拒绝 */
    private Post resolveHostOrNull(String targetType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        if (!"POST".equalsIgnoreCase(targetType)) {
            // WISH/CAPSULE/LETTER 等宿主域可见性语义在各自服务，未接入前不猜测
            throw new BusinessException("HOST_TYPE_UNSUPPORTED",
                    "附件宿主类型暂仅支持 POST: " + targetType);
        }
        Long postId;
        try {
            postId = Long.valueOf(targetId);
        } catch (NumberFormatException ex) {
            throw new BusinessException("HOST_TARGET_INVALID", "宿主 ID 非法");
        }
        Post host = postMapper.selectById(postId);
        if (host == null) {
            // 逻辑删除或不存在：按不存在处理（不泄露存在性差异）
            throw new BusinessException("RESOURCE_NOT_VISIBLE", "内容不可见");
        }
        return host;
    }
}

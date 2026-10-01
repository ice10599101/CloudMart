package com.cloudmart.community.policy;

import com.cloudmart.community.entity.Post;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 社区内容宿主访问策略（C01）：评论/投票/问卷等下游互动继承宿主可读状态。
 *
 * <p>帖子可读谓词唯一：未删除 + status=1（已发布，非草稿/非平台隐藏）+ 审核通过。
 * 帖子被平台隐藏（status 变更）或软删后，评论/点赞等下游互动同步不可访问——
 * 不能只保护详情页。</p>
 */
@Component
public class ContentAccessPolicy {

    /** 帖子公开可读：未删除 + 已发布 + 审核通过（作者与普通用户同一谓词，隐藏后下游同步收敛） */
    public boolean isPostReadable(Post post) {
        return post != null
                && post.getDeletedAt() == null
                && Integer.valueOf(1).equals(post.getStatus())
                && Integer.valueOf(1).equals(post.getReviewStatus());
    }

    /** 要求可读；不满足统一 404 语义（不泄露隐藏/删除的存在性） */
    public void requirePostReadable(Post post) {
        if (!isPostReadable(post)) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
    }
}

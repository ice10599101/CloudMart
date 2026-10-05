package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.service.AccountErasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号数据擦除（T06 补齐：E10 实测 COMMUNITY 域 ERASURE_DOMAIN_NOT_WIRED）。
 *
 * <p>口径与 wish 域一致（B20）：内容软删保留审计、实体表清痕、PII 去标识化；
 * 幂等——重复调用无害（UPDATE 条件天然幂等，DELETE 影响行数为 0 视作已擦）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureServiceImpl implements AccountErasureService {

    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;

    @Override
    @Transactional
    public boolean eraseUserData(Long userId) {
        // 帖子：软删（保留审计计数），标题/正文去标识化避免管理端回显原文
        int posts = postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getUserId, userId)
                .set(Post::getTitle, "已注销用户内容")
                .set(Post::getContent, "")
                .set(Post::getCoverImage, null)
                .set(Post::getMediaUrls, null)
                .set(Post::getStatus, 2)
                .set(Post::getDeletedAt, LocalDateTime.now()));
        // 评论：软删 + 正文清空
        int comments = postCommentMapper.update(null, new LambdaUpdateWrapper<PostComment>()
                .eq(PostComment::getUserId, userId)
                .set(PostComment::getContent, "")
                .set(PostComment::getDeletedAt, LocalDateTime.now()));
        log.warn("T06 编排擦除社区数据 userId={}, posts={}, comments={}", userId, posts, comments);
        return true;
    }
}

package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostShare;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.PostShareMapper;
import com.cloudmart.community.service.PostShareService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.vo.PostShareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostShareServiceImpl implements PostShareService {

    private final PostShareMapper postShareMapper;
    private final PostMapper postMapper;
    private final UserEnrichmentService userEnrichmentService;
    private final CommunityEventProducer communityEventProducer;

    @Override
    @Transactional
    public PostShareVO sharePost(Long userId, Long postId, String channel) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }

        PostShare share = new PostShare();
        share.setPostId(postId);
        share.setUserId(userId);
        share.setChannel(channel != null ? channel : "LINK");
        postShareMapper.insert(share);

        post.setShareCount(post.getShareCount() != null ? post.getShareCount() + 1 : 1);
        postMapper.updateById(post);

        if (!userId.equals(post.getUserId())) {
            communityEventProducer.publishShareEvent(post.getUserId(), userId, postId, post.getTitle(), share.getChannel());
        }

        UserInfo user = userEnrichmentService.getSingleUser(userId);
        return new PostShareVO(
                share.getId(),
                postId,
                userId,
                user.nickname(),
                user.avatar(),
                share.getChannel(),
                share.getCreatedAt()
        );
    }

    @Override
    public List<PostShareVO> getPostShares(Long postId, int page, int size) {
        int safeLimit = Math.min(Math.max(size, 1), 50);
        int offset = (Math.max(page, 1) - 1) * safeLimit;

        List<PostShare> shares = postShareMapper.selectList(
                new LambdaQueryWrapper<PostShare>()
                        .eq(PostShare::getPostId, postId)
                        .orderByDesc(PostShare::getCreatedAt)
                        .last("LIMIT " + safeLimit + " OFFSET " + offset));

        if (shares.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = shares.stream().map(PostShare::getUserId).distinct().toList();
        Map<Long, UserInfo> userMap = userEnrichmentService.batchGetUsers(new java.util.HashSet<>(userIds));

        return shares.stream()
                .map(share -> {
                    UserInfo user = userMap.getOrDefault(share.getUserId(), new UserInfo(share.getUserId(), "未知用户", "", null, null));
                    return new PostShareVO(
                            share.getId(),
                            share.getPostId(),
                            share.getUserId(),
                            user.nickname(),
                            user.avatar(),
                            share.getChannel(),
                            share.getCreatedAt()
                    );
                })
                .toList();
    }
}

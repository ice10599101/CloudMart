package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateCommentRequest;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.service.ContentReviewService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.LikeService;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.vo.CommentVO;
import com.cloudmart.community.vo.PostCommentVO;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PostCommentServiceImpl implements PostCommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w\\u4e00-\\u9fa5]+)");

    private final PostCommentMapper postCommentMapper;
    private final PostMapper postMapper;
    private final CommunityEventProducer communityEventProducer;
    private final GrowthService growthService;
    private final UserEnrichmentService userEnrichmentService;
    private final ContentReviewService contentReviewService;
    private final LikeService likeService;

    public PostCommentServiceImpl(PostCommentMapper postCommentMapper,
                                  PostMapper postMapper,
                                  CommunityEventProducer communityEventProducer,
                                  GrowthService growthService,
                                  UserEnrichmentService userEnrichmentService,
                                  ContentReviewService contentReviewService,
                                  LikeService likeService) {
        this.postCommentMapper = postCommentMapper;
        this.postMapper = postMapper;
        this.communityEventProducer = communityEventProducer;
        this.growthService = growthService;
        this.userEnrichmentService = userEnrichmentService;
        this.contentReviewService = contentReviewService;
        this.likeService = likeService;
    }

    @Override
    @Transactional
    public PostCommentVO createComment(Long userId, Long postId, CreateCommentRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }

        ContentReviewService.ReviewResult reviewResult = contentReviewService.reviewContent(request.content());
        if (!reviewResult.approved()) {
            throw new BusinessException("CONTENT_REJECTED", reviewResult.reason());
        }

        PostComment comment = new PostComment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setParentId(request.parentId());
        comment.setReplyToUserId(request.replyToUserId());
        comment.setContent(reviewResult.filteredContent());
        comment.setLikeCount(0);
        comment.setStatus(0);
        comment.setReviewStatus(reviewResult.needsManualReview() ? 0 : 1);
        postCommentMapper.insert(comment);

        post.setCommentCount(post.getCommentCount() != null ? post.getCommentCount() + 1 : 1);
        postMapper.updateById(post);

        growthService.addExp(userId, 10, "COMMENT", comment.getId(), "发表评论");
        communityEventProducer.publishCommentEvent(post.getUserId(), userId, postId, post.getTitle(), request.content());

        publishMentionNotifications(request.content(), userId, postId, post.getTitle());

        Set<Long> userIds = new HashSet<>();
        userIds.add(comment.getUserId());
        if (comment.getReplyToUserId() != null) {
            userIds.add(comment.getReplyToUserId());
        }
        Map<Long, UserInfo> userMap = userEnrichmentService.batchGetUsers(userIds);

        return buildCommentVO(comment, userMap, false);
    }

    @Override
    public Page<PostCommentVO> getComments(Long postId, int page, int size, Long currentUserId) {
        LambdaQueryWrapper<PostComment> topWrapper = new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPostId, postId)
                .eq(PostComment::getStatus, 0)
                .eq(PostComment::getReviewStatus, 1)
                .isNull(PostComment::getParentId)
                .orderByDesc(PostComment::getCreatedAt);

        Page<PostComment> topPage = postCommentMapper.selectPage(new Page<>(page, size), topWrapper);

        // 一次性查询所有顶级评论的回复，避免 N+1
        Map<Long, List<PostComment>> repliesMap = new java.util.HashMap<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> allCommentIds = new HashSet<>();
        for (PostComment top : topPage.getRecords()) {
            userIds.add(top.getUserId());
            allCommentIds.add(top.getId());
            if (top.getReplyToUserId() != null) {
                userIds.add(top.getReplyToUserId());
            }
            List<PostComment> replies = postCommentMapper.selectList(
                    new LambdaQueryWrapper<PostComment>()
                            .eq(PostComment::getParentId, top.getId())
                            .eq(PostComment::getStatus, 0)
                            .eq(PostComment::getReviewStatus, 1)
                            .orderByAsc(PostComment::getCreatedAt)
            );
            repliesMap.put(top.getId(), replies);
            for (PostComment reply : replies) {
                userIds.add(reply.getUserId());
                allCommentIds.add(reply.getId());
                if (reply.getReplyToUserId() != null) {
                    userIds.add(reply.getReplyToUserId());
                }
            }
        }
        Map<Long, UserInfo> userMap = userEnrichmentService.batchGetUsers(userIds);

        // 批量查询当前用户对所有评论和回复的点赞状态，避免 N+1
        Map<Long, Boolean> likedMap = (currentUserId != null && !allCommentIds.isEmpty())
                ? likeService.batchIsLiked(currentUserId, "COMMENT", List.copyOf(allCommentIds))
                : Map.of();

        List<PostCommentVO> voList = topPage.getRecords().stream()
                .map(comment -> {
                    List<PostComment> replies = repliesMap.getOrDefault(comment.getId(), List.of());
                    boolean commentIsLiked = likedMap.getOrDefault(comment.getId(), false);
                    PostCommentVO vo = buildCommentVO(comment, userMap, commentIsLiked);
                    List<PostCommentVO> replyVOs = replies.stream()
                            .map(reply -> buildCommentVO(reply, userMap, likedMap.getOrDefault(reply.getId(), false)))
                            .toList();
                    return new PostCommentVO(
                            vo.id(), vo.postId(), vo.userId(), vo.authorNickname(), vo.authorAvatar(),
                            vo.parentId(), vo.replyToUserId(), vo.replyToNickname(), vo.content(),
                            vo.likeCount(), vo.status(), vo.isLiked(), replyVOs, vo.createdAt()
                    );
                })
                .toList();

        Page<PostCommentVO> resultPage = new Page<>(topPage.getCurrent(), topPage.getSize(), topPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public Page<CommentVO> getMyComments(Long userId, int page, int size) {
        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getUserId, userId)
                .eq(PostComment::getReviewStatus, 1)
                .orderByDesc(PostComment::getCreatedAt);

        Page<PostComment> commentPage = postCommentMapper.selectPage(new Page<>(page, size), wrapper);

        Set<Long> replyToUserIds = new HashSet<>();
        for (PostComment c : commentPage.getRecords()) {
            if (c.getReplyToUserId() != null) {
                replyToUserIds.add(c.getReplyToUserId());
            }
        }
        Map<Long, UserInfo> userMap = userEnrichmentService.batchGetUsers(replyToUserIds);

        List<CommentVO> voList = commentPage.getRecords().stream()
                .map(comment -> {
                    Post post = postMapper.selectById(comment.getPostId());
                    String postTitle = post != null ? post.getTitle() : null;

                    String replyToNickname = null;
                    if (comment.getReplyToUserId() != null) {
                        UserInfo replyToUser = userMap.getOrDefault(comment.getReplyToUserId(),
                                new UserInfo(comment.getReplyToUserId(), "用户" + comment.getReplyToUserId(), null, null, null));
                        replyToNickname = replyToUser.nickname();
                    }

                    return new CommentVO(
                            comment.getId(),
                            comment.getPostId(),
                            postTitle,
                            comment.getContent(),
                            comment.getParentId(),
                            replyToNickname,
                            comment.getLikeCount(),
                            comment.getStatus(),
                            comment.getCreatedAt()
                    );
                })
                .toList();

        Page<CommentVO> resultPage = new Page<>(commentPage.getCurrent(), commentPage.getSize(), commentPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("COMMENT_NOT_FOUND", "评论不存在");
        }
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException("COMMENT_FORBIDDEN", "无权删除此评论");
        }

        postCommentMapper.deleteById(commentId);

        Post post = postMapper.selectById(comment.getPostId());
        if (post != null && post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(post.getCommentCount() - 1);
            postMapper.updateById(post);
        }
    }

    @Override
    public void likeComment(Long userId, Long commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("COMMENT_NOT_FOUND", "评论不存在");
        }

        if (!likeService.like(userId, "COMMENT", commentId)) {
            throw new BusinessException("ALREADY_LIKED", "已点赞该评论");
        }
    }

    @Override
    public void unlikeComment(Long userId, Long commentId) {
        likeService.unlike(userId, "COMMENT", commentId);
    }

    @Override
    public Page<PostCommentVO> adminListComments(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PostComment::getContent, keyword);
        }
        if (status != null) {
            wrapper.eq(PostComment::getStatus, status);
        }
        wrapper.orderByDesc(PostComment::getCreatedAt);

        Page<PostComment> commentPage = postCommentMapper.selectPage(new Page<>(page, size), wrapper);

        Set<Long> userIds = new HashSet<>();
        for (PostComment c : commentPage.getRecords()) {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                userIds.add(c.getReplyToUserId());
            }
        }
        Map<Long, UserInfo> userMap = userEnrichmentService.batchGetUsers(userIds);

        List<PostCommentVO> voList = commentPage.getRecords().stream()
                .map(comment -> buildCommentVO(comment, userMap, false))
                .toList();

        Page<PostCommentVO> resultPage = new Page<>(commentPage.getCurrent(), commentPage.getSize(), commentPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional
    public void adminUpdateCommentStatus(Long commentId, Integer status) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("COMMENT_NOT_FOUND", "评论不存在");
        }
        comment.setStatus(status);
        postCommentMapper.updateById(comment);
    }

    @Override
    @Transactional
    public void adminDeleteComment(Long commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("COMMENT_NOT_FOUND", "评论不存在");
        }
        postCommentMapper.deleteById(commentId);
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null && post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(post.getCommentCount() - 1);
            postMapper.updateById(post);
        }
    }

    private PostCommentVO buildCommentVO(PostComment comment, Map<Long, UserInfo> userMap, boolean isLiked) {
        UserInfo author = userMap.getOrDefault(comment.getUserId(),
                new UserInfo(comment.getUserId(), "用户" + comment.getUserId(), null, null, null));

        String replyToNickname = null;
        if (comment.getReplyToUserId() != null) {
            UserInfo replyToUser = userMap.getOrDefault(comment.getReplyToUserId(),
                    new UserInfo(comment.getReplyToUserId(), "用户" + comment.getReplyToUserId(), null, null, null));
            replyToNickname = replyToUser.nickname();
        }

        return new PostCommentVO(
                comment.getId(),
                comment.getPostId(),
                comment.getUserId(),
                author.nickname(),
                author.avatar(),
                comment.getParentId(),
                comment.getReplyToUserId(),
                replyToNickname,
                comment.getContent(),
                comment.getLikeCount(),
                comment.getStatus(),
                isLiked,
                Collections.emptyList(),
                comment.getCreatedAt()
        );
    }

    private void publishMentionNotifications(String content, Long operatorUserId, Long postId, String postTitle) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> mentionedNicknames = new HashSet<>();
        while (matcher.find()) {
            mentionedNicknames.add(matcher.group(1));
        }
        if (mentionedNicknames.isEmpty()) {
            return;
        }

        for (String nickname : mentionedNicknames) {
            try {
                var response = userEnrichmentService.searchUsersByNickname(nickname);
                if (response != null) {
                    for (var entry : response.entrySet()) {
                        if (!entry.getKey().equals(operatorUserId)) {
                            communityEventProducer.publishMentionEvent(entry.getKey(), operatorUserId, postId, postTitle);
                        }
                    }
                }
            } catch (Exception e) {
                // mention notifications are best-effort
            }
        }
    }
}

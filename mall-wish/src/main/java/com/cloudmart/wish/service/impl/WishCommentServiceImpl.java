package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishCommentRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishComment;
import com.cloudmart.wish.enums.WishCommentStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishCommentMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.WishCommentService;
import com.cloudmart.wish.vo.WishCommentCreateVO;
import com.cloudmart.wish.vo.WishCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 心愿评论服务实现。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>二级回复扁平化：回复子评论时 parentId 自动挂载到其顶级评论，
 *       前端按 parentId 组装层级（避免深嵌套查询）</li>
 *   <li>先发后审：敏感词命中仅标记 sensitive_hit，不阻断发布（文档 4.4）</li>
 *   <li>reply_to_user_id 冗余存储，列表时批量解析昵称，避免逐条联查</li>
 *   <li>HIDDEN 评论对普通用户不可见（管理后台下架四端立即生效）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishCommentServiceImpl implements WishCommentService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final WishCommentMapper wishCommentMapper;
    private final WishMapper wishMapper;
    private final WishContentSanitizer contentSanitizer;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WishCommentCreateVO createComment(Long userId, Long wishId, CreateWishCommentRequest request) {
        requireCommentableWish(wishId, userId);

        // 内容净化：路径穿越拦截 + XSS 转义 + 敏感词标记（先发后审）
        String raw = request.content().trim();
        if (!contentSanitizer.isFreeOfPathTraversal(raw)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "评论内容包含非法字符");
        }
        String escaped = contentSanitizer.escapeHtml(raw);
        boolean sensitiveHit = contentSanitizer.containsSensitiveWord(raw);

        // 父评论校验与二级扁平化
        Long parentId = null;
        Long replyToUserId = null;
        if (request.parentId() != null) {
            WishComment parent = wishCommentMapper.selectById(request.parentId());
            if (parent == null || !wishId.equals(parent.getWishId())
                    || parent.getStatus() != WishCommentStatus.VISIBLE) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "回复的评论不存在或已下架");
            }
            replyToUserId = parent.getUserId();
            parentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
        }

        WishComment comment = new WishComment();
        comment.setWishId(wishId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(escaped);
        comment.setLikeCount(0);
        comment.setStatus(WishCommentStatus.VISIBLE);
        comment.setSensitiveHit(sensitiveHit);
        wishCommentMapper.insert(comment);

        if (sensitiveHit) {
            log.info("评论命中敏感词已标记待审, wishId={}, userId={}, commentId={}", wishId, userId, comment.getId());
        }
        log.debug("评论发表成功, wishId={}, userId={}, commentId={}", wishId, userId, comment.getId());
        return new WishCommentCreateVO(comment.getId(), escaped, comment.getCreatedAt());
    }

    @Override
    public CommentPage listComments(Long wishId, Long viewerId, String cursor, Integer pageSize) {
        requireCommentableWish(wishId, viewerId);
        int safeSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Long cursorId = parseCursor(cursor);

        LambdaQueryWrapper<WishComment> wrapper = new LambdaQueryWrapper<WishComment>()
                .eq(WishComment::getWishId, wishId)
                .eq(WishComment::getStatus, WishCommentStatus.VISIBLE)
                .orderByDesc(WishComment::getId)
                .last("LIMIT " + (safeSize + 1));
        if (cursorId != null) {
            wrapper.lt(WishComment::getId, cursorId);
        }

        List<WishComment> comments = wishCommentMapper.selectList(wrapper);
        boolean hasMore = comments.size() > safeSize;
        List<WishComment> pageItems = hasMore ? comments.subList(0, safeSize) : comments;

        List<WishCommentVO> records = toVOsWithUserInfo(pageItems);
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? String.valueOf(pageItems.get(pageItems.size() - 1).getId()) : null;
        return new CommentPage(records, nextCursor, hasMore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long wishId, Long commentId) {
        requireCommentableWish(wishId, userId);

        WishComment comment = wishCommentMapper.selectById(commentId);
        if (comment == null || !wishId.equals(comment.getWishId())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "评论不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "只能删除自己的评论");
        }

        wishCommentMapper.deleteById(commentId); // @TableLogic 软删，保留审计
        log.info("评论删除成功, wishId={}, userId={}, commentId={}", wishId, userId, commentId);
    }

    // ---------------- 私有方法 ----------------

    /**
     * 心愿可评论性校验：与互动可见性语义一致（PRIVATE/TREE_HOLE 非作者、审核隐藏 → 404）。
     */
    private void requireCommentableWish(Long wishId, Long userId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || wish.getDeletedAt() != null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        boolean isAuthor = wish.getUserId().equals(userId);
        if (!isAuthor && (wish.getVisibility() != WishVisibility.PUBLIC
                || Boolean.FALSE.equals(wish.getIsVisible()))) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
    }

    private List<WishCommentVO> toVOsWithUserInfo(List<WishComment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        // 评论者 + 被回复者统一批量解析（避免 N+1）
        Set<Long> userIds = comments.stream().flatMap(c -> {
            List<Long> ids = new ArrayList<>();
            ids.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                ids.add(c.getReplyToUserId());
            }
            return ids.stream();
        }).collect(Collectors.toSet());

        Map<Long, CommentUserInfo> infoMap = fetchUserInfo(userIds);
        return comments.stream()
                .map(c -> {
                    CommentUserInfo author = infoMap.getOrDefault(c.getUserId(), CommentUserInfo.placeholder(c.getUserId()));
                    String replyToNickname = c.getReplyToUserId() != null
                            ? infoMap.getOrDefault(c.getReplyToUserId(),
                                    CommentUserInfo.placeholder(c.getReplyToUserId())).nickname()
                            : null;
                    return new WishCommentVO(
                            c.getId(),
                            c.getWishId(),
                            c.getUserId(),
                            author.nickname(),
                            author.avatar(),
                            c.getContent(),
                            c.getParentId(),
                            replyToNickname,
                            c.getCreatedAt());
                })
                .toList();
    }

    /**
     * 批量获取用户昵称与头像（Feign，降级返回空 Map，展示层用占位值）。
     */
    private Map<Long, CommentUserInfo> fetchUserInfo(Set<Long> userIds) {
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> new CommentUserInfo(
                                        ((Number) m.get("id")).longValue(),
                                        (String) m.getOrDefault("nickname", "心愿旅人"),
                                        (String) m.getOrDefault("avatar", ""))));
            }
        } catch (Exception e) {
            log.warn("批量获取评论用户信息失败，降级为占位数据: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 评论用户展示信息。
     */
    private record CommentUserInfo(Long userId, String nickname, String avatar) {
        static CommentUserInfo placeholder(Long userId) {
            return new CommentUserInfo(userId, "心愿旅人", "");
        }
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的游标格式");
        }
    }
}

package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminCommentListQuery;
import com.cloudmart.wish.dto.AdminCommentStatusRequest;
import com.cloudmart.wish.entity.WishComment;
import com.cloudmart.wish.enums.WishCommentStatus;
import com.cloudmart.wish.repository.WishCommentMapper;
import com.cloudmart.wish.service.AdminCommentService;
import com.cloudmart.wish.vo.AdminCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台评论服务实现（Sprint 1.2）。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>列表用 selectPageIncludingDeleted 绕过 @TableLogic，保留已删除评论供审计</li>
 *   <li>上下架仅作用于未软删评论：用户已删除的评论返回 404，不再变更状态</li>
 *   <li>状态未变化时返回 409 冲突，幂等地避免无效写操作</li>
 *   <li>使用 updateById 而非 LambdaUpdateWrapper，规避单元测试 lambda cache 问题</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCommentServiceImpl implements AdminCommentService {

    private static final String NICKNAME_PLACEHOLDER = "心愿旅人";

    private final WishCommentMapper wishCommentMapper;
    private final AdminDisplayInfoResolver displayInfoResolver;

    @Override
    public Page<AdminCommentVO> listComments(AdminCommentListQuery query) {
        LambdaQueryWrapper<WishComment> wrapper = new LambdaQueryWrapper<>();
        if (query.wishId() != null) {
            wrapper.eq(WishComment::getWishId, query.wishId());
        }
        if (query.userId() != null) {
            wrapper.eq(WishComment::getUserId, query.userId());
        }
        if (query.sensitiveHit() != null) {
            wrapper.eq(WishComment::getSensitiveHit, query.sensitiveHit());
        }
        if (query.status() != null) {
            wrapper.eq(WishComment::getStatus, query.status());
        }
        wrapper.orderByDesc(WishComment::getId);

        Page<WishComment> page = new Page<>(query.page(), query.pageSize());
        Page<WishComment> commentPage = wishCommentMapper.selectPageIncludingDeleted(page, wrapper);

        List<WishComment> records = commentPage.getRecords();
        Map<Long, String> titleMap = displayInfoResolver.fetchWishTitles(collectWishIds(records));
        Map<Long, String> nicknameMap = displayInfoResolver.fetchUserNicknames(collectUserIds(records));

        List<AdminCommentVO> voList = records.stream()
                .map(c -> toVO(c, titleMap, nicknameMap))
                .toList();

        Page<AdminCommentVO> resultPage =
                new Page<>(commentPage.getCurrent(), commentPage.getSize(), commentPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCommentVO updateCommentStatus(Long commentId, AdminCommentStatusRequest request) {
        // selectById 经 @TableLogic 过滤：用户已软删的评论不可再上下架
        WishComment comment = wishCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "评论不存在");
        }

        WishCommentStatus target = request.status();
        if (comment.getStatus() == target) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "评论已是目标状态: " + target);
        }

        WishComment updateEntity = new WishComment();
        updateEntity.setId(commentId);
        updateEntity.setStatus(target);
        wishCommentMapper.updateById(updateEntity);

        log.info("评论状态已变更, commentId={}, {} -> {}", commentId, comment.getStatus(), target);

        WishComment updated = wishCommentMapper.selectById(commentId);
        Map<Long, String> titleMap =
                displayInfoResolver.fetchWishTitles(Set.of(updated.getWishId()));
        Map<Long, String> nicknameMap =
                displayInfoResolver.fetchUserNicknames(Set.of(updated.getUserId()));
        return toVO(updated, titleMap, nicknameMap);
    }

    private Set<Long> collectWishIds(List<WishComment> records) {
        return records.stream().map(WishComment::getWishId).collect(Collectors.toSet());
    }

    private Set<Long> collectUserIds(List<WishComment> records) {
        return records.stream().map(WishComment::getUserId).collect(Collectors.toSet());
    }

    private AdminCommentVO toVO(WishComment comment,
                                Map<Long, String> titleMap,
                                Map<Long, String> nicknameMap) {
        return new AdminCommentVO(
                comment.getId(),
                comment.getWishId(),
                titleMap.getOrDefault(comment.getWishId(), ""),
                comment.getUserId(),
                nicknameMap.getOrDefault(comment.getUserId(), NICKNAME_PLACEHOLDER),
                comment.getContent(),
                comment.getParentId(),
                comment.getStatus(),
                comment.getSensitiveHit(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}

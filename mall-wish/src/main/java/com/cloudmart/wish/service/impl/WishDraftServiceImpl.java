package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishDraft;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishDraftMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.WishDraftService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.util.WishJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 心愿草稿服务（N03）：每人最多 20 份；只本人可见；不进公共 feed、不发奖励；
 * publish 复用 createWish 同一领域命令并同事务关联 publishedWishId（发布幂等）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishDraftServiceImpl implements WishDraftService {

    private static final int MAX_DRAFTS = 20;

    private final WishDraftMapper draftMapper;
    private final WishMapper wishMapper;
    private final WishService wishService;

    public record SaveDraftRequest(String clientDraftId, String title, String description,
                                   Long categoryId, List<String> mediaUrls, List<String> tags,
                                   LocalDateTime expectedAt, String expectedTimezone,
                                   String visibility, Long version) {
    }

    @Override
    @Transactional
    public WishDraft saveDraft(Long userId, String clientDraftId, SaveDraftRequest request) {
        String draftKey = clientDraftId == null || clientDraftId.isBlank()
                ? java.util.UUID.randomUUID().toString() : clientDraftId.trim();

        WishDraft existing = draftMapper.selectOne(new LambdaQueryWrapper<WishDraft>()
                .eq(WishDraft::getUserId, userId)
                .eq(WishDraft::getClientDraftId, draftKey)
                .last("LIMIT 1"));

        if (existing != null) {
            // 乐观锁自动保存：version 不符 → 409（客户端保留本地内容后刷新）
            if (request.version() != null && existing.getVersion() != null
                    && !request.version().equals(existing.getVersion())) {
                throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT,
                        "草稿已被其他端修改，请刷新");
            }
            applyRequest(existing, request);
            existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
            draftMapper.updateById(existing);
            return existing;
        }

        Long count = draftMapper.selectCount(new LambdaQueryWrapper<WishDraft>()
                .eq(WishDraft::getUserId, userId));
        if (count != null && count >= MAX_DRAFTS) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "草稿数量已达上限（20 份）");
        }

        WishDraft draft = new WishDraft();
        draft.setUserId(userId);
        draft.setClientDraftId(draftKey);
        applyRequest(draft, request);
        draft.setVersion(0);
        try {
            draftMapper.insert(draft);
        } catch (DuplicateKeyException ex) {
            // 并发同 clientDraftId：按幂等返回已存在草稿
            WishDraft winner = draftMapper.selectOne(new LambdaQueryWrapper<WishDraft>()
                    .eq(WishDraft::getUserId, userId)
                    .eq(WishDraft::getClientDraftId, draftKey)
                    .last("LIMIT 1"));
            if (winner != null) {
                return winner;
            }
            throw new BusinessException(WishErrorCodes.WISH_OPERATION_IN_PROGRESS, "草稿保存冲突，请重试");
        }
        return draft;
    }

    private void applyRequest(WishDraft draft, SaveDraftRequest request) {
        if (request.title() != null) {
            draft.setTitle(request.title());
        }
        if (request.description() != null) {
            draft.setDescription(request.description());
        }
        if (request.categoryId() != null) {
            draft.setCategoryId(request.categoryId());
        }
        if (request.mediaUrls() != null) {
            draft.setMediaUrls(WishJsonUtils.stringifyList(request.mediaUrls()));
        }
        if (request.tags() != null) {
            draft.setTags(WishJsonUtils.stringifyList(request.tags()));
        }
        if (request.expectedAt() != null) {
            draft.setExpectedAt(request.expectedAt());
        }
        if (request.expectedTimezone() != null) {
            draft.setExpectedTimezone(request.expectedTimezone());
        }
        if (request.visibility() != null) {
            draft.setVisibility(request.visibility());
        }
    }

    @Override
    public List<WishDraft> listMyDrafts(Long userId, Long cursor, int pageSize) {
        return draftMapper.selectList(new LambdaQueryWrapper<WishDraft>()
                .eq(WishDraft::getUserId, userId)
                .lt(cursor != null, WishDraft::getId, cursor)
                .orderByDesc(WishDraft::getId)
                .last("LIMIT " + Math.min(Math.max(pageSize, 1), 50)));
    }

    @Override
    @Transactional
    public void deleteDraft(Long userId, Long draftId, Long version) {
        WishDraft draft = requireOwnedDraft(userId, draftId);
        if (version != null && draft.getVersion() != null && !version.equals(draft.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "草稿已被并发修改");
        }
        draftMapper.deleteById(draftId);
    }

    @Override
    @Transactional
    public Wish publishDraft(Long userId, Long draftId, CreateWishRequest request) {
        WishDraft draft = requireOwnedDraft(userId, draftId);
        if (draft.getPublishedWishId() != null) {
            // 发布幂等：已发布的草稿返回既有心愿，不二次发布/发奖
            Wish published = wishMapper.selectById(draft.getPublishedWishId());
            if (published != null) {
                return published;
            }
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "草稿关联心愿不存在");
        }
        // 复用发布领域命令（B04 幂等/B10 统计/事件均由该命令承载）
        com.cloudmart.wish.vo.WishCreateResultVO result = wishService.createWish(userId, request);
        draft.setPublishedWishId(result.id());
        draft.setVersion(draft.getVersion() == null ? 1 : draft.getVersion() + 1);
        draftMapper.updateById(draft);
        log.info("草稿发布完成, draftId={}, wishId={}, userId={}", draftId, result.id(), userId);
        return wishMapper.selectById(result.id());
    }

    @Override
    public WishDraft requireOwnedDraft(Long userId, Long draftId) {
        WishDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !draft.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "草稿不存在");
        }
        return draft;
    }
}

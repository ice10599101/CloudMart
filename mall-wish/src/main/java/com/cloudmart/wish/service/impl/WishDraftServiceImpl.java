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
            // W01：乐观锁 CAS——@Version 由插件在 updateById 上自动追加
            // version=expected 条件并递增；0 行 = 两设备并发保存（QA27）→ 409 带服务端版本
            if ("PUBLISHED".equals(existing.getStatus()) || "PUBLISHING".equals(existing.getStatus())) {
                throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "草稿已发布，不可编辑");
            }
            if (request.version() != null && existing.getVersion() != null
                    && !request.version().equals(existing.getVersion())) {
                throw versionConflict(existing);
            }
            applyRequest(existing, request);
            int updated = draftMapper.updateById(existing);
            if (updated == 0) {
                throw versionConflict(draftMapper.selectById(existing.getId()));
            }
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
        // W01：删除与发布竞争结果确定——PUBLISHING 中不可删（等发布终态）；
        // PUBLISHED 不可删（发布映射即幂等凭据，撤回走心愿自身流程）
        if ("PUBLISHING".equals(draft.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_OPERATION_IN_PROGRESS, "草稿发布处理中，请稍后重试");
        }
        if ("PUBLISHED".equals(draft.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "草稿已发布，不可删除");
        }
        if (version != null && draft.getVersion() != null && !version.equals(draft.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "草稿已被并发修改");
        }
        int deleted = draftMapper.deleteById(draftId);
        if (deleted == 0) {
            // 并发删除/发布竞争：以 DB 终态为准
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "草稿状态已变更，请刷新");
        }
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
        // W01：CAS DRAFT→PUBLISHING——两设备不同幂等键同时发布（QA27）只有一个赢家；
        // 败者阻塞在本 UPDATE 行锁上，赢家提交后 0 行 → 重读重放/报冲突
        int claimed = draftMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WishDraft>()
                        .eq(WishDraft::getId, draftId)
                        .eq(WishDraft::getStatus, "DRAFT")
                        .isNull(WishDraft::getPublishedWishId)
                        .set(WishDraft::getStatus, "PUBLISHING"));
        if (claimed == 0) {
            WishDraft now = draftMapper.selectById(draftId);
            if (now != null && now.getPublishedWishId() != null) {
                return publishReplay(now);
            }
            throw new BusinessException(WishErrorCodes.WISH_OPERATION_IN_PROGRESS,
                    "草稿发布处理中，请稍后按原请求重试");
        }

        // 复用发布领域命令（B04 幂等/B10 统计/事件均由该命令承载）；同事务：
        // createWish 失败整体回滚（含 PUBLISHING 状态），无半发布残留
        com.cloudmart.wish.vo.WishCreateResultVO result = wishService.createWish(userId, request);
        draft.setStatus("PUBLISHED");
        draft.setPublishedWishId(result.id());
        int finalized = draftMapper.updateById(draft);
        if (finalized == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "草稿发布状态已变更");
        }
        log.info("草稿发布完成, draftId={}, wishId={}, userId={}", draftId, result.id(), userId);
        return wishMapper.selectById(result.id());
    }

    /** W01：发布重放——按草稿映射返回已发布心愿 */
    private Wish publishReplay(WishDraft draft) {
        Wish published = wishMapper.selectById(draft.getPublishedWishId());
        if (published != null) {
            return published;
        }
        throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "草稿关联心愿不存在");
    }

    /** W01：版本冲突 409——带服务端当前版本，客户端选择重载或另存副本 */
    private BusinessException versionConflict(WishDraft server) {
        return new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT,
                "草稿已被其他端修改，请刷新（服务端版本 "
                        + (server == null || server.getVersion() == null ? "未知" : server.getVersion()) + "）");
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

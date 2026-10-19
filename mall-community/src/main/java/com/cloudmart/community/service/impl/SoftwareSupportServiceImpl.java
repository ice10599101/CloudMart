package com.cloudmart.community.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.dto.software.InteractionContextRequest;
import com.cloudmart.community.dto.software.SoftwareReviewRequest;
import com.cloudmart.community.service.ContentReviewService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.SoftwareSupportService;
import com.cloudmart.community.service.UserBlockService;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;
import com.cloudmart.community.vo.software.SoftwareInteractionContextVO;
import com.cloudmart.community.vo.software.SoftwareLevelOptionVO;
import com.cloudmart.community.vo.software.SoftwareLevelVO;
import com.cloudmart.community.vo.software.SoftwareReviewOutcomeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 小答软件域只读适配实现（方案 9.6）。
 *
 * <p>失败策略：等级/拉黑查询异常必须拒绝受限操作（fail-closed 抛出 503 语义异常），
 * 绝不返回虚假的"等级 1"或"未拉黑"；审核失败由软件服务处置为不得发布。</p>
 */
@Service
@RequiredArgsConstructor
public class SoftwareSupportServiceImpl implements SoftwareSupportService {

    private static final int MAX_TEXTS = 100;
    private static final int MAX_TEXT_LENGTH = 20_000;
    private static final int MAX_PEERS = 100;
    private static final String PEER_BLOCKED = "PEER_BLOCKED";

    private final GrowthService growthService;
    private final com.cloudmart.community.repository.PostMapper postMapper;
    private final com.cloudmart.community.policy.ContentAccessPolicy contentAccessPolicy;
    private final ContentReviewService contentReviewService;
    private final UserBlockService userBlockService;
    private final Clock clock;

    @Override
    public SoftwareLevelVO level(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID", "userId 非法");
        }
        UserLevelVO userLevel;
        try {
            // 现有成长服务规则：首次查询即创建 1 级记录——沿用，不另建成长体系
            userLevel = growthService.getUserLevel(userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE",
                    "社区等级查询不可用", e);
        }
        if (userLevel == null || userLevel.level() == null) {
            throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区等级查询不可用");
        }
        return new SoftwareLevelVO(userLevel.userId(), userLevel.level(),
                userLevel.levelTitle(), clock.instant());
    }

    @Override
    public List<SoftwareLevelOptionVO> levelOptions() {
        List<LevelConfigVO> configs;
        try {
            // 现有实现已过滤 status=1 并按 level 升序
            configs = growthService.getLevelConfigs();
        } catch (Exception e) {
            throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "等级配置查询不可用", e);
        }
        return configs.stream()
                .map(config -> new SoftwareLevelOptionVO(config.level(), config.title()))
                .toList();
    }

    @Override
    public SoftwareReviewOutcomeVO review(SoftwareReviewRequest request) {
        validateReviewRequest(request);
        List<String> filteredTexts = new ArrayList<>(request.texts().size());
        boolean allApproved = true;
        boolean needsManualReview = request.mediaCount() != null && request.mediaCount() > 0;
        String firstReason = needsManualReview ? "含图片/视频内容，待人工审核后展示" : null;

        for (String text : request.texts()) {
            ContentReviewService.ReviewResult result = contentReviewService.reviewContent(text);
            filteredTexts.add(result.filteredContent() == null ? text : result.filteredContent());
            if (!result.approved()) {
                allApproved = false;
                if (firstReason == null) {
                    firstReason = result.reason();
                }
            }
            if (result.needsManualReview()) {
                needsManualReview = true;
                if (firstReason == null) {
                    firstReason = result.reason();
                }
            }
        }
        return new SoftwareReviewOutcomeVO(request.contentHash(), allApproved, needsManualReview,
                List.copyOf(filteredTexts), firstReason, clock.instant());
    }

    @Override
    public SoftwareInteractionContextVO interactionContext(InteractionContextRequest request) {
        if (request.userId() == null || request.userId() <= 0) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID", "userId 非法");
        }
        List<Long> peers = request.peerUserIds();
        if (peers == null || peers.isEmpty() || peers.size() > MAX_PEERS) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID",
                    "peerUserIds 必须为 1~" + MAX_PEERS + " 个");
        }
        List<Long> blockedOrBlocker;
        try {
            blockedOrBlocker = userBlockService.getBlockedOrBlockerIds(request.userId());
        } catch (Exception e) {
            // fail-closed：拉黑关系查询失败时拒绝受限操作，绝不按"未拉黑"放行
            throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "拉黑关系查询不可用", e);
        }
        Set<Long> blockedSet = new LinkedHashSet<>(blockedOrBlocker);
        List<Long> blockedPeers = peers.stream().distinct()
                .filter(blockedSet::contains)
                .toList();
        boolean canInteract = blockedPeers.isEmpty();
        return new SoftwareInteractionContextVO(request.userId(), canInteract,
                canInteract ? null : PEER_BLOCKED, blockedPeers, clock.instant());
    }

    private void validateReviewRequest(SoftwareReviewRequest request) {
        if (request.requestId() == null || request.requestId().length() < 8
                || request.requestId().length() > 128) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID", "requestId 长度须为 8~128");
        }
        if (request.contentHash() == null || !request.contentHash().matches("[0-9a-fA-F-]{8,128}")) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID", "contentHash 格式非法");
        }
        List<String> texts = request.texts();
        if (texts == null || texts.isEmpty() || texts.size() > MAX_TEXTS) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID",
                    "texts 必须为 1~" + MAX_TEXTS + " 条");
        }
        for (String text : texts) {
            if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
                throw new BusinessException("SOFTWARE_SUPPORT_INVALID",
                        "单条文本须为 1~" + MAX_TEXT_LENGTH + " 字符");
            }
        }
        if (request.mediaCount() != null && request.mediaCount() < 0) {
            throw new BusinessException("SOFTWARE_SUPPORT_INVALID", "mediaCount 不能为负");
        }
    }

    // ------------------------------------------------------------------
    // UX04：帖子公开状态（软件教程入口；严格沿用帖子可读谓词）
    // ------------------------------------------------------------------

    @Override
    public java.util.List<java.util.Map<String, Object>> postPublicStatus(java.util.List<Long> postIds) {
        java.util.List<Long> ids = postIds == null ? java.util.List.of()
                : postIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<com.cloudmart.community.entity.Post> posts =
                postMapper.selectBatchIds(ids);
        java.util.Map<Long, com.cloudmart.community.entity.Post> byId = new java.util.HashMap<>();
        for (com.cloudmart.community.entity.Post post : posts) {
            byId.put(post.getId(), post);
        }
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Long id : ids) {
            com.cloudmart.community.entity.Post post = byId.get(id);
            boolean readable = contentAccessPolicy.isPostReadable(post);
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("postId", String.valueOf(id));
            row.put("public", readable);
            if (readable && post != null) {
                row.put("title", post.getTitle());
                String content = post.getContent() == null ? "" : post.getContent();
                row.put("summary", content.length() > 120 ? content.substring(0, 120) : content);
                row.put("coverImage", post.getCoverImage());
                row.put("updatedAt", post.getUpdatedAt() == null ? null : post.getUpdatedAt().toString());
            }
            result.add(row);
        }
        return result;
    }
}

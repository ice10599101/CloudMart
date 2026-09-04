package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AdminCommentSearchRequest;
import com.cloudmart.admin.dto.feign.AdminInteractionSearchRequest;
import com.cloudmart.admin.dto.feign.AdminWishSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class WishFeignClientFallbackFactory implements FallbackFactory<WishFeignClient> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 下游业务错误（信封 error.code/message，如"活动条件尚未达成"）原样透传，
     * 仅真正的连接/超时类故障才降级为 WISH_SERVICE_UNAVAILABLE。
     */
    private static BusinessException unwrapBusinessError(Throwable cause) {
        if (cause instanceof FeignException fe) {
            String body = fe.contentUTF8();
            if (body != null && !body.isBlank()) {
                try {
                    JsonNode error = MAPPER.readTree(body).path("error");
                    String code = error.path("code").asText("");
                    if (!code.isEmpty()) {
                        return new BusinessException(code, error.path("message").asText("请求失败"));
                    }
                } catch (Exception ignore) {
                    // 非信封响应体，按普通降级处理
                }
            }
        }
        return null;
    }

    @Override
    public WishFeignClient create(Throwable cause) {
        BusinessException businessError = unwrapBusinessError(cause);
        if (businessError != null) {
            log.warn("心愿服务返回业务错误: code={}, message={}", businessError.getCode(), businessError.getMessage());
            throw businessError;
        }
        log.error("心愿服务调用失败: {}", cause.getMessage());
        return new WishFeignClient() {
            @Override
            public ApiResponse<Object> listWishes(AdminWishSearchRequest request) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getWish(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> auditWish(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listCategories() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createCategory(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateCategory(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteCategory(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listInteractions(AdminInteractionSearchRequest request) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listComments(AdminCommentSearchRequest request) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateCommentStatus(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listBadges() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createBadge(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateBadge(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateBadgeStatus(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listBgmSongs() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createBgmSong(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateBgmSong(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateBgmSongStatus(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteBgmSong(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listAiPrompts(String scene) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createAiPrompt(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateAiPromptStatus(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listAiConfigs() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateAiConfig(String configKey, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listMatchGroups(String status, String keyword) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> forceDissolveMatchGroup(Long groupId) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listMatchConfigs() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateMatchConfig(String configKey, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listContentFlowLogs(String status, Integer page, Integer size) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> retryContentFlow(Long logId) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> legacyStats() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listLeaderboardConfigs() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateLeaderboardConfig(String configKey, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listGrayscaleConfigs() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateGrayscaleRatio(String featureKey, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> generateAiReviewSamples(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listAiReviewSamples(String scene, String result, Integer page, Integer size) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> scoreAiReviewSample(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> aiReviewStats() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> mapAudit() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listFences(Long wishId) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createFence(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateFence(Long fenceId, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> toggleFence(Long fenceId, boolean active) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> deleteFence(Long fenceId) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listWarmEventsForAdmin(String auditStatus, Integer page, Integer size) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> auditWarmEvent(Long eventId, String auditStatus) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listActivitiesForAdmin(String status, String type, Integer page, Integer size) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createActivity(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateActivity(Long id, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> transitionActivity(Long id, String action) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> deleteActivity(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> issueActivityRewards(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listActivityRewardLogs(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listWishAssets() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> saveWishAsset(Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> toggleWishAssetActive(Long id, boolean active) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> deleteWishAsset(Long id) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listAllBrandsForAdmin() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> auditWishBrand(Long id, String status) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listSuspicious(Long userId) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listFreezes() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> unfreezeUser(Long userId) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listLiveWidgetConfigs() {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> saveLiveWidgetConfig(Long streamerId, Map<String, Object> data) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> toggleLiveWidgetVisible(Long streamerId, boolean visible) {
                throw new BusinessException("WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
            }
        };
    }
}

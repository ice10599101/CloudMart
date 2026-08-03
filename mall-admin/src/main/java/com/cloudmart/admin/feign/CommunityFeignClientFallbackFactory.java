package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CommunityFeignClientFallbackFactory implements FallbackFactory<CommunityFeignClient> {

    @Override
    public CommunityFeignClient create(Throwable cause) {
        log.error("社区服务调用失败: {}", cause.getMessage());
        return new CommunityFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> getStatsOverview() {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<List<Map<String, Object>>> getStatsTrend(int days) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listPosts(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> updatePostStatus(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> togglePostTop(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deletePost(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listComments(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> updateCommentStatus(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteComment(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listTags(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createTag(Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateTag(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteTag(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> updateTagStatus(Long id, Integer status) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listReports(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> handleReport(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listBadges(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createBadge(Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateBadge(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteBadge(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> updateBadgeStatus(Long id, Integer status) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> grantBadge(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listLevelConfigs(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createLevelConfig(Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateLevelConfig(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteLevelConfig(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> updateGrowthLevelStatus(Long id, Integer status) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listPendingReviewPosts(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> approvePost(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> rejectPost(Long id, Map<String, String> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listSensitiveWords(Map<String, Object> params) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> addSensitiveWord(Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateSensitiveWord(Long id, Map<String, Object> data) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteSensitiveWord(Long id) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> refreshSensitiveWordCache() {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }
        };
    }
}

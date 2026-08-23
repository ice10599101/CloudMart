package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AdminCommentSearchRequest;
import com.cloudmart.admin.dto.feign.AdminInteractionSearchRequest;
import com.cloudmart.admin.dto.feign.AdminWishSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class WishFeignClientFallbackFactory implements FallbackFactory<WishFeignClient> {

    @Override
    public WishFeignClient create(Throwable cause) {
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
        };
    }
}

package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.InteractionContextRequest;
import com.cloudmart.software.dto.software.SoftwareInteractionContextVO;
import com.cloudmart.software.dto.software.SoftwareLevelOptionVO;
import com.cloudmart.software.dto.software.SoftwareLevelVO;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.dto.software.SoftwareReviewRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 社区适配降级工厂（9.6 失败策略）：等级/拉黑/审核失败必须拒绝对应受限操作，
 * 绝不返回虚假的"等级 1 / 未拉黑 / 审核通过"。统一 503 语义码由调用方决定
 * 转为可重试错误还是功能不可用。
 */
@Slf4j
@Component
public class CommunitySupportFeignClientFallbackFactory
        implements FallbackFactory<CommunitySupportFeignClient> {

    @Override
    public CommunitySupportFeignClient create(Throwable cause) {
        log.warn("mall-community 软件支持接口不可用: {}", cause.getMessage());
        return new CommunitySupportFeignClient() {
            @Override
            public ApiResponse<SoftwareLevelVO> getLevel(Long userId) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE",
                        "社区等级服务暂不可用，请稍后重试", cause);
            }

            @Override
            public ApiResponse<List<SoftwareLevelOptionVO>> getLevelConfigs() {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE",
                        "等级配置服务暂不可用，请稍后重试", cause);
            }

            @Override
            public ApiResponse<SoftwareReviewOutcomeVO> review(SoftwareReviewRequest request) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE",
                        "内容审核服务暂不可用，提交已保留，请稍后重试", cause);
            }

            @Override
            public ApiResponse<SoftwareInteractionContextVO> interactionContext(
                    InteractionContextRequest request) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE",
                        "互动资格服务暂不可用，请稍后重试", cause);
            }

            @Override
            public ApiResponse<java.util.List<java.util.Map<String, Object>>> postPublicStatus(
                    java.util.List<Long> ids) {
                throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE",
                        "教程内容服务暂不可用，请稍后重试", cause);
            }
        };
    }
}

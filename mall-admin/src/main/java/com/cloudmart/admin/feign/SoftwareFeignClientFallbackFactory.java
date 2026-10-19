package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * mall-software 管理端降级工厂：全部管理动作失败如实 503，
 * 绝不返回"已通过/已开通/已处置"假象（A22：管理动作必须真实落到软件库）。
 */
@Slf4j
@Component
public class SoftwareFeignClientFallbackFactory implements FallbackFactory<SoftwareFeignClient> {

    @Override
    public SoftwareFeignClient create(Throwable cause) {
        log.warn("mall-software 管理端接口不可用: {}", cause.getMessage());
        return new SoftwareFeignClient() {
            @Override
            public ApiResponse<Object> decision(Long revisionId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> sourceState(Long softwareId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> sources(Long softwareId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> pending(int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> adminDetail(Long softwareId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> comments(Long softwareId, String status, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> moderateComment(Long softwareId, Long commentId,
                                                       Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> permissions(int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> permissionsOf(List<Long> userIds) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> togglePermission(Long userId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> grants(Long softwareId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> grant(Long softwareId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> revokeGrant(Long softwareId, Long userId,
                                                   Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> contacts() {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> saveContact(Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> reports(String status, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> handleReport(Long reportId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> linkFeedbacks(String status, String reasonCode,
                                                     int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> markFeedback(Long feedbackId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> reviewDetail(Long revisionId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> items(String keyword, Long ownerId, Long categoryId,
                                             String license, String status, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> ban(Long softwareId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> commentsGlobal(Long softwareId, String status,
                                                      int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> deleteSoftware(Long softwareId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> unban(Long softwareId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> featured(Long softwareId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> categories() {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> createCategory(Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> updateCategory(Long categoryId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> disableCategory(Long categoryId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> tags(String keyword, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> createTag(Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> updateTag(Long tagId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> disableTag(Long tagId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> grantPage(Long softwareId, Long userId, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> permissionAudits(Long userId, Long softwareId,
                                                        int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> topics(int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> topicDetail(Long topicId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> createTopic(Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> updateTopic(Long topicId, Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> disableTopic(Long topicId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> statistics() {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> fanoutTasks(int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> publisherApplications(String status, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> accessRequests(String status, int page, int size) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> decideAccessRequest(Long requestId,
                                                           java.util.Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> decidePublisherApplication(Long applicationId,
                                                                  java.util.Map<String, Object> body) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> retryFanoutTask(Long taskId) {
                throw unavailable(cause);
            }

            private BusinessException unavailable(Throwable root) {
                return new BusinessException("SOFTWARE_SERVICE_UNAVAILABLE",
                        "软件服务暂不可用，请稍后重试", root);
            }
        };
    }
}

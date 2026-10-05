package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AdminUpdateUserRequest;
import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.dto.feign.UserDTO;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MemberUserFeignClientFallbackFactory implements FallbackFactory<MemberUserFeignClient> {

    @Override
    public MemberUserFeignClient create(Throwable cause) {
        log.error("用户服务调用失败: {}", cause.getMessage());
        return new MemberUserFeignClient() {
            @Override
            public ApiResponse<List<UserDTO>> listUsers(int page, int size, String username, String nickname, Integer status) {
                throw FeignBusinessErrors.parse(cause, "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<UserDTO> getUserById(Long id) {
                throw FeignBusinessErrors.parse(cause, "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<UserDTO> updateUser(Long id, AdminUpdateUserRequest request) {
                throw FeignBusinessErrors.parse(cause, "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> resetPassword(Long id, java.util.Map<String, String> body) {
                throw FeignBusinessErrors.parse(cause, "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> toggleUserStatus(Long id, Integer status) {
                throw FeignBusinessErrors.parse(cause, "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CountResponse> getMemberCount() {
                throw FeignBusinessErrors.parse(cause, "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }
        };
    }
}

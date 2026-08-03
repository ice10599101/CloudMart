package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AdminUpdateUserRequest;
import com.cloudmart.admin.dto.feign.CountResponse;
import com.cloudmart.admin.dto.feign.UserDTO;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
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
            public ApiResponse<List<UserDTO>> listUsers(int page, int size) {
                throw new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<UserDTO> getUserById(Long id) {
                throw new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<UserDTO> updateUser(Long id, AdminUpdateUserRequest request) {
                throw new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> toggleUserStatus(Long id, Integer status) {
                throw new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CountResponse> getMemberCount() {
                throw new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
            }
        };
    }
}

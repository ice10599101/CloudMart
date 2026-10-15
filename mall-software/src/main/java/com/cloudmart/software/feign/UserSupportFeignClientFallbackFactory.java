package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 用户资料适配降级工厂：账号资格验证失败必须拒绝对应受限操作；
 * 资料查询失败由调用方降级为通用头像/昵称展示，绝不返回虚构资料。
 */
@Slf4j
@Component
public class UserSupportFeignClientFallbackFactory
        implements FallbackFactory<UserSupportFeignClient> {

    @Override
    public UserSupportFeignClient create(Throwable cause) {
        log.warn("mall-user 软件支持接口不可用: {}", cause.getMessage());
        return body -> {
            throw new BusinessException("USER_SERVICE_UNAVAILABLE",
                    "用户资料服务暂不可用，请稍后重试", cause);
        };
    }
}

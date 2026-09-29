package com.cloudmart.admin.feign;

import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * 兑换码 Feign 降级工厂：与 {@link PetFeignClientFallbackFactory} 同一口径——
 * 下游业务错误原样透传，只有连接/超时类故障才降级为
 * {@code COUPON_SERVICE_UNAVAILABLE}。动态代理生成整表抛同一种降级异常的实例，
 * 新增方法无需逐个补降级。
 */
@Component
@Slf4j
public class ExchangeCodeFeignClientFallbackFactory implements FallbackFactory<ExchangeCodeFeignClient> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ExchangeCodeFeignClient create(Throwable cause) {
        BusinessException businessError = unwrapBusinessError(cause);
        if (businessError != null) {
            log.warn("优惠券服务返回业务错误: code={}, message={}",
                    businessError.getCode(), businessError.getMessage());
            throw businessError;
        }
        log.error("优惠券服务调用失败: {}", cause.getMessage());
        BusinessException unavailable = new BusinessException("COUPON_SERVICE_UNAVAILABLE",
                "优惠券服务不可用，请稍后重试");
        return (ExchangeCodeFeignClient) Proxy.newProxyInstance(
                ExchangeCodeFeignClient.class.getClassLoader(),
                new Class<?>[]{ExchangeCodeFeignClient.class},
                (proxy, method, args) -> {
                    throw unavailable;
                });
    }

    /** 下游业务错误透传（Feign 会把 4xx/5xx 包成 FeignException，信封里带 error.code） */
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
}

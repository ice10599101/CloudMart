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
 * 宠物服务 Feign 降级工厂。
 *
 * <p>与 WishFeignClientFallbackFactory 同一口径：下游业务错误（信封 error.code/message）
 * 原样透传，只有连接/超时类故障才降级为 {@code PET_SERVICE_UNAVAILABLE}（全局异常处理映射 503）。</p>
 *
 * <p>实现上用动态代理生成"整表抛同一种降级异常"的实例：PetFeignClient 方法较多，
 * 逐个手写匿名实现只会带来维护噪音（新增方法忘写 → 运行时 NPE）。</p>
 */
@Component
@Slf4j
public class PetFeignClientFallbackFactory implements FallbackFactory<PetFeignClient> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public PetFeignClient create(Throwable cause) {
        BusinessException businessError = unwrapBusinessError(cause);
        if (businessError != null) {
            log.warn("宠物服务返回业务错误: code={}, message={}",
                    businessError.getCode(), businessError.getMessage());
            throw businessError;
        }
        log.error("宠物服务调用失败: {}", cause.getMessage());
        BusinessException unavailable = new BusinessException("PET_SERVICE_UNAVAILABLE",
                "宠物服务不可用，请稍后重试");
        return (PetFeignClient) Proxy.newProxyInstance(
                PetFeignClient.class.getClassLoader(),
                new Class<?>[]{PetFeignClient.class},
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

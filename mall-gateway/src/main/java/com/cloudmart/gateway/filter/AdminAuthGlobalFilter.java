package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class AdminAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        // B01：管理员角色只能来自 JwtAuthenticationFilter 在验签成功后注入的
        // X-Admin-Role 头。禁止在此处直接 parse 未经签名校验的 JWT 恢复身份——
        // 伪造签名但携带 scope=admin 声明的 token 会被该路径提权。
        String role = exchange.getRequest().getHeaders().getFirst(SecurityConstants.ADMIN_ROLE_HEADER);

        if (role == null || role.isEmpty()) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录或登录已过期");
        }

        if (!"admin".equals(role)) {
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "需要管理员权限");
        }

        return chain.filter(exchange);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = "{\"success\":false,\"data\":{},\"error\":{\"code\":\""
                + code + "\",\"message\":\"" + message + "\",\"details\":[]},\"meta\":{}}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1001;
    }
}

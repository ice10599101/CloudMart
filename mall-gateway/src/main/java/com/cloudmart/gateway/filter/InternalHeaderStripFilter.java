package com.cloudmart.gateway.filter;

import com.cloudmart.common.constant.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class InternalHeaderStripFilter implements GlobalFilter, Ordered {

    private static final String[] HEADERS_TO_STRIP = {
            SecurityConstants.INTERNAL_CALL_HEADER,
            SecurityConstants.USER_ID_HEADER,
            SecurityConstants.ADMIN_ROLE_HEADER,
            SecurityConstants.ADMIN_PERMISSIONS_HEADER,
            SecurityConstants.ADMIN_DEPT_ID_HEADER,
            SecurityConstants.ADMIN_USERNAME_HEADER,
            SecurityConstants.X_REQUEST_ID_HEADER
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(builder -> builder
                        .headers(headers -> {
                            for (String header : HEADERS_TO_STRIP) {
                                if (headers.remove(header) != null) {
                                    log.debug("Stripped external header: {}", header);
                                }
                            }
                        }))
                .build();

        return chain.filter(sanitizedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

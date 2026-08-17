package com.cloudmart.wish.config;

import com.cloudmart.common.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 内部服务间调用认证过滤器。
 *
 * <p>网关在转发请求时注入 {@code X-Internal-Call: true} 和 {@code X-User-Id: <userId>} 头，
 * 本过滤器将其转化为 Spring Security {@code Authentication}，使下游 Controller 可通过
 * {@code @RequestHeader("X-User-Id")} 获取当前用户。</p>
 *
 * <p>安全保障：该过滤器仅在网关已验证 JWT 后才注入头，未经过网关的直接请求
 * 不会携带 {@code X-Internal-Call} 头（网关会剥离外部伪造的同名头）。</p>
 */
@Component
public class InternalCallAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_CALL_VALUE = "true";
    private static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String internalCallHeader = request.getHeader(SecurityConstants.INTERNAL_CALL_HEADER);

        if (INTERNAL_CALL_VALUE.equals(internalCallHeader)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String userIdHeader = request.getHeader(SecurityConstants.USER_ID_HEADER);
            String principal = userIdHeader != null ? userIdHeader : "INTERNAL_SERVICE";

            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null,
                    List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}

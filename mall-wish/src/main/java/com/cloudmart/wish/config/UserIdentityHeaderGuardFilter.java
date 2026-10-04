package com.cloudmart.wish.config;

import com.cloudmart.common.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * T02 用户域身份头护栏：{@code X-User-Id} 在用户域路径上是身份，不是数据。
 *
 * <p>公共 {@code UserJwtAuthenticationFilter} 在用户/管理员 JWT 验签成功后把
 * X-User-Id 强制改写为令牌主体（封堵直连伪造）；本过滤器补齐另一半：请求未建立
 * JWT 身份（匿名，或仅携带服务令牌却落在用户域路径）时剥离该头——伪造的
 * X-User-Id 不能在 permitAll 公开端点（心愿详情、地图、还愿故事等）冒充其他
 * 用户触发私密资源判定。</p>
 *
 * <p>服务域路径（/admin、/internal）不剥离：可信服务调用方以服务令牌建立
 * INTERNAL 身份后，X-User-Id 是其携带的目标用户数据字段（如定时任务指定的
 * 用户），剥离会破坏内部流程。</p>
 */
public class UserIdentityHeaderGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SERVICE_DOMAIN_PREFIXES = Set.of("/admin", "/internal");
    private static final SimpleGrantedAuthority ROLE_USER_AUTHORITY =
            new SimpleGrantedAuthority("ROLE_USER");
    private static final SimpleGrantedAuthority ROLE_ADMIN_AUTHORITY =
            new SimpleGrantedAuthority("ROLE_ADMIN");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isServiceDomain(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (hasJwtEstablishedIdentity()) {
            // 用户/管理员 JWT 身份：公共过滤器已把 X-User-Id 改写为令牌主体，原样放行
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new ErasedUserIdHeaderRequest(request), response);
    }

    /** 仅用户/管理员 JWT 建立的身份放行；匿名与 ROLE_INTERNAL 等其他主体一律剥离。 */
    private boolean hasJwtEstablishedIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && (authentication.getAuthorities().contains(ROLE_USER_AUTHORITY)
                        || authentication.getAuthorities().contains(ROLE_ADMIN_AUTHORITY));
    }

    /** @return 服务域路径（/admin、/internal，含 ;matrix 参数容错） */
    static boolean isServiceDomain(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        String path = requestUri;
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }
        for (String prefix : SERVICE_DOMAIN_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** 剥离 X-User-Id 的请求包装：getHeader 返回 null、getHeaders 为空、头名列表不含该键 */
    private static final class ErasedUserIdHeaderRequest extends HttpServletRequestWrapper {

        ErasedUserIdHeaderRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(name -> SecurityConstants.USER_ID_HEADER.equalsIgnoreCase(name));
            return Collections.enumeration(names);
        }
    }
}

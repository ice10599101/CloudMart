package com.cloudmart.wish.config;

import com.cloudmart.common.security.ServiceTokenCodec;
import com.cloudmart.common.security.ServiceTokenCodec.ServiceTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * B01 服务间调用认证过滤器：只认 {@code X-Service-Token} 短期签名令牌，不再信任裸头。
 *
 * <p>路径 → (允许签发方, 能力域) 强映射：</p>
 * <ul>
 *   <li>{@code /admin/**} → mall-admin + wish:admin（后台管理代理）</li>
 *   <li>{@code /internal/jobs/**}、{@code /internal/tree-env/**} → mall-job + wish:jobs（定时任务）</li>
 *   <li>{@code /internal/pet-support/**} → mall-pet + wish:pet（宠物钱包/捞瓶）</li>
 * </ul>
 *
 * <p>令牌不匹配当前路径要求的 iss/scope 时一律拒绝建立 INTERNAL 身份；受保护端点随后
 * 由 Spring Security 401。用户请求无需携带服务令牌；携带无效令牌不会获得任何身份。</p>
 */
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenAuthenticationFilter.class);

    static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    private static final Map<String, ServiceTokenRequirement> PATH_REQUIREMENTS = Map.of(
            "/admin", new ServiceTokenRequirement("mall-admin", "wish:admin"),
            "/internal/jobs", new ServiceTokenRequirement("mall-job", "wish:jobs"),
            "/internal/tree-env", new ServiceTokenRequirement("mall-job", "wish:jobs"),
            "/internal/pet-support", new ServiceTokenRequirement("mall-pet", "wish:pet")
    );

    private final WishSecurityProperties properties;
    private final Clock clock;

    public ServiceTokenAuthenticationFilter(WishSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ServiceTokenRequirement requirement = resolveRequirement(request.getRequestURI());
        String token = request.getHeader(ServiceTokenCodec.HEADER_NAME);

        if (requirement != null && token != null && !token.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                ServiceTokenCodec.ServiceTokenClaims claims = ServiceTokenCodec.verify(
                        token, properties.serviceTokenSecret(), "mall-wish",
                        requirement.allowedIssuer(), requirement.requiredScope(),
                        clock.instant(), Duration.ofSeconds(properties.clockSkewSeconds()));
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                claims.issuer(), null, List.of(new SimpleGrantedAuthority(ROLE_INTERNAL)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ServiceTokenException e) {
                // 拒绝但不中断：受保护端点将得到 401；公开端点保持匿名语义
                log.warn("[B01 REJECT] 服务令牌校验失败 path={} error={} detail={}",
                        request.getRequestURI(), e.getError(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * @return 该路径的令牌要求；不在受保护前缀内返回 null（无需服务令牌）
     */
    static ServiceTokenRequirement resolveRequirement(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        String path = requestUri;
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }
        for (Map.Entry<String, ServiceTokenRequirement> entry : PATH_REQUIREMENTS.entrySet()) {
            String prefix = entry.getKey();
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }

    record ServiceTokenRequirement(String allowedIssuer, String requiredScope) {
    }
}

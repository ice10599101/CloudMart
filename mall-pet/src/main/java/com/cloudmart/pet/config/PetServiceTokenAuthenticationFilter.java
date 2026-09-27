package com.cloudmart.pet.config;

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
 * SEC-01 服务间调用认证过滤器：只认 {@code X-Service-Token} 短期签名令牌，
 * 不再信任裸 {@code X-Internal-Call} 头（普通用户 JWT 经网关注入的头永远拿不到服务身份）。
 *
 * <p>路径 → (允许签发方, 能力域) 强映射：</p>
 * <ul>
 *   <li>{@code /admin/**} → mall-admin + pet:admin（后台管理代理）</li>
 * </ul>
 *
 * <p>令牌不匹配当前路径要求的 iss/scope 时一律拒绝建立 INTERNAL 身份；受保护端点随后
 * 由 Spring Security 401。用户请求无需携带服务令牌；携带无效令牌不会获得任何身份。
 * 后续任务调度/迁移等内部端点须以独立 scope 扩充映射，不得复用 pet:admin。</p>
 */
public class PetServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PetServiceTokenAuthenticationFilter.class);

    static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    private static final Map<String, ServiceTokenRequirement> PATH_REQUIREMENTS = Map.of(
            "/admin", new ServiceTokenRequirement("mall-admin", "pet:admin")
    );

    private final PetSecurityProperties properties;
    private final Clock clock;

    public PetServiceTokenAuthenticationFilter(PetSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ServiceTokenRequirement requirement = resolveRequirement(request.getRequestURI());
        String token = request.getHeader(ServiceTokenCodec.HEADER_NAME);

        // 密钥不可用时进入 fail-closed：拒绝所有服务令牌（绝不放行未验签调用）
        boolean validationAvailable = properties.isServiceTokenValidationAvailable();

        if (requirement != null && token != null && !token.isBlank() && validationAvailable
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                ServiceTokenCodec.ServiceTokenClaims claims = ServiceTokenCodec.verify(
                        token, properties.getServiceTokenSecret(), "mall-pet",
                        requirement.allowedIssuer(), requirement.requiredScope(),
                        clock.instant(), Duration.ofSeconds(properties.getClockSkewSeconds()));
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                claims.issuer(), null, List.of(new SimpleGrantedAuthority(ROLE_INTERNAL)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ServiceTokenException e) {
                // 拒绝但不中断：受保护端点将得到 401；公开端点保持匿名语义
                log.warn("[SEC-01 REJECT] 服务令牌校验失败 path={} error={} detail={}",
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

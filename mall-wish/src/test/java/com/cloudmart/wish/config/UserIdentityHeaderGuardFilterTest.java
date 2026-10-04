package com.cloudmart.wish.config;

import com.cloudmart.common.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserIdentityHeaderGuardFilter 单元测试（T02）")
class UserIdentityHeaderGuardFilterTest {

    @Mock
    private FilterChain filterChain;

    private final UserIdentityHeaderGuardFilter guardFilter = new UserIdentityHeaderGuardFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String uri, String userIdHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (userIdHeader != null) {
            request.addHeader(SecurityConstants.USER_ID_HEADER, userIdHeader);
        }
        return request;
    }

    private void authenticateWith(String role) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "1001", null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 过滤器可能以包装请求放行——断言对象必须是下游实际收到的请求 */
    private HttpServletRequest requestPassedToChain() throws Exception {
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), org.mockito.ArgumentMatchers.eq(response));
        return captor.getValue();
    }

    @Test
    @DisplayName("匿名 + 伪造 X-User-Id 访问用户域公开端点：头被剥离")
    void anonymous_forgedHeader_erased() throws Exception {
        MockHttpServletRequest request = request("/wishes/2001", "1002");

        guardFilter.doFilter(request, response, filterChain);

        HttpServletRequest downstream = requestPassedToChain();
        assertThat(downstream.getHeader(SecurityConstants.USER_ID_HEADER)).isNull();
        assertThat(downstream.getHeaders(SecurityConstants.USER_ID_HEADER).hasMoreElements()).isFalse();
    }

    @Test
    @DisplayName("匿名无头请求：透传不包装（无伪造头即无剥离动作）")
    void anonymous_noHeader_passThrough() throws Exception {
        MockHttpServletRequest request = request("/wishes/2001", null);

        guardFilter.doFilter(request, response, filterChain);

        assertThat(requestPassedToChain().getHeader(SecurityConstants.USER_ID_HEADER)).isNull();
    }

    @Test
    @DisplayName("用户 JWT 身份（公共过滤器已把头改写为 sub）：头保留")
    void userJwtIdentity_headerPreserved() throws Exception {
        authenticateWith("ROLE_USER");
        MockHttpServletRequest request = request("/wishes/2001", "1001");

        guardFilter.doFilter(request, response, filterChain);

        assertThat(requestPassedToChain().getHeader(SecurityConstants.USER_ID_HEADER)).isEqualTo("1001");
    }

    @Test
    @DisplayName("管理员 JWT 身份：头保留（管理员访问用户域以自身主体运行）")
    void adminJwtIdentity_headerPreserved() throws Exception {
        authenticateWith("ROLE_ADMIN");
        MockHttpServletRequest request = request("/wishes/2001", "9001");

        guardFilter.doFilter(request, response, filterChain);

        assertThat(requestPassedToChain().getHeader(SecurityConstants.USER_ID_HEADER)).isEqualTo("9001");
    }

    @Test
    @DisplayName("服务令牌身份落在用户域路径：防御性剥离（路径映射错配时不泄露身份头）")
    void internalRoleOnUserDomain_erased() throws Exception {
        authenticateWith("ROLE_INTERNAL");
        MockHttpServletRequest request = request("/wishes/2001", "1002");

        guardFilter.doFilter(request, response, filterChain);

        assertThat(requestPassedToChain().getHeader(SecurityConstants.USER_ID_HEADER)).isNull();
    }

    @Test
    @DisplayName("服务域路径（/internal/jobs）+ 服务身份：X-User-Id 是数据字段，保留")
    void serviceDomain_internalRole_headerPreserved() throws Exception {
        authenticateWith("ROLE_INTERNAL");
        MockHttpServletRequest request = request("/internal/jobs/capsule-scan", "1001");

        guardFilter.doFilter(request, response, filterChain);

        assertThat(requestPassedToChain().getHeader(SecurityConstants.USER_ID_HEADER)).isEqualTo("1001");
    }

    @Test
    @DisplayName("服务域路径（/admin）匿名请求：不剥离（端点由 @PreAuthorize 401 兜底）")
    void serviceDomain_anonymous_passThrough() throws Exception {
        MockHttpServletRequest request = request("/admin/wishes", "1002");

        guardFilter.doFilter(request, response, filterChain);

        assertThat(requestPassedToChain().getHeader(SecurityConstants.USER_ID_HEADER)).isEqualTo("1002");
    }

    @Test
    @DisplayName(";matrix 参数容错：/admin;jsessionid=xx 仍识别为服务域")
    void serviceDomain_matrixParams_recognized() {
        assertThat(UserIdentityHeaderGuardFilter.isServiceDomain("/admin;jsessionid=x")).isTrue();
        assertThat(UserIdentityHeaderGuardFilter.isServiceDomain("/internal/tree-env")).isTrue();
        assertThat(UserIdentityHeaderGuardFilter.isServiceDomain("/administrator/x")).isFalse();
        assertThat(UserIdentityHeaderGuardFilter.isServiceDomain("/wishes")).isFalse();
        assertThat(UserIdentityHeaderGuardFilter.isServiceDomain(null)).isFalse();
    }

    @Test
    @DisplayName("头名列表剥离：getHeaderNames 不再含 X-User-Id，其余头保留")
    void headerNames_excludesUserIdHeader() throws Exception {
        MockHttpServletRequest request = request("/wishes/2001", "1002");
        request.addHeader("X-Request-Id", "req-1");

        guardFilter.doFilter(request, response, filterChain);

        var allNames = new ArrayList<String>();
        requestPassedToChain().getHeaderNames().asIterator().forEachRemaining(allNames::add);
        assertThat(allNames).doesNotContain(SecurityConstants.USER_ID_HEADER);
        assertThat(allNames).contains("X-Request-Id");
    }
}

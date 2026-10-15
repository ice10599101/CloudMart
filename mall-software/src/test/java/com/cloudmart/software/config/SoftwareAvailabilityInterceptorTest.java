package com.cloudmart.software.config;

import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 11.2 总开关（software.enabled）拦截器单测：
 * 关闭后公共软件读写返回功能不可用；受控编辑入口（草稿读取/保存、撤回/下架等）保留。
 */
class SoftwareAvailabilityInterceptorTest {

    private final SoftwareProperties properties = new SoftwareProperties();
    private final SoftwareAvailabilityInterceptor interceptor =
            new SoftwareAvailabilityInterceptor(properties);

    private boolean preHandle(String method, String path) {
        return interceptor.preHandle(new MockHttpServletRequest(method, path),
                new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("总开关开启：全部放行")
    void enabled_passesAll() {
        properties.setEnabled(true);

        assertThat(preHandle("GET", "/software")).isTrue();
        assertThat(preHandle("POST", "/software/1/comments")).isTrue();
        assertThat(preHandle("POST", "/software/1/sources/2/resolve")).isTrue();
    }

    @Test
    @DisplayName("总开关关闭：公共读被拒（列表/详情/评论/榜单/专题）")
    void disabled_blocksPublicRead() {
        assertThatThrownBy(() -> preHandle("GET", "/software"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_DISABLED");
        assertThatThrownBy(() -> preHandle("GET", "/software/1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/1/releases"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/1/comments"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/categories"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/tags"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/rankings"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/topics"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("GET", "/software/topics/some-slug"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("总开关关闭：公共写被拒（评论/点赞/收藏/评分/订阅/举报/获取链接/反馈）")
    void disabled_blocksPublicWrite() {
        assertThatThrownBy(() -> preHandle("POST", "/software/1/comments"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("PUT", "/software/1/comments/9/like"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("PUT", "/software/1/favorite"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("PUT", "/software/1/rating"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("PUT", "/software/1/subscription"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("POST", "/software/1/reports"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("POST", "/software/1/sources/2/resolve"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preHandle("POST", "/software/1/sources/2/feedback"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("总开关关闭：作者受控入口保留（草稿读取/保存、撤回/下架/重新上架/删除）")
    void disabled_allowsControlledEntries() {
        assertThat(preHandle("POST", "/software")).isTrue();
        assertThat(preHandle("GET", "/software/mine")).isTrue();
        assertThat(preHandle("GET", "/software/publisher-status")).isTrue();
        assertThat(preHandle("GET", "/software/preferences")).isTrue();
        assertThat(preHandle("PUT", "/software/preferences")).isTrue();
        assertThat(preHandle("GET", "/software/1/editor")).isTrue();
        assertThat(preHandle("POST", "/software/1/working-revision")).isTrue();
        assertThat(preHandle("PUT", "/software/1/revisions/2")).isTrue();
        assertThat(preHandle("POST", "/software/1/revisions/2/submit")).isTrue();
        assertThat(preHandle("POST", "/software/1/revisions/2/withdraw")).isTrue();
        assertThat(preHandle("POST", "/software/1/unlist")).isTrue();
        assertThat(preHandle("POST", "/software/1/relist")).isTrue();
        assertThat(preHandle("DELETE", "/software/1")).isTrue();
    }

    @Test
    @DisplayName("总开关关闭：OPTIONS 预检放行")
    void disabled_allowsOptions() {
        assertThat(preHandle("OPTIONS", "/software")).isTrue();
    }
}

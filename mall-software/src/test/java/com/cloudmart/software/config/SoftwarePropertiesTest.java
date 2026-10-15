package com.cloudmart.software.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 功能开关默认值不变量（方案 11.2）：上线前全部关闭、发布资格默认拒绝。
 *
 * <p>这些默认值是安全边界：若有人误把默认值改为开放，本测试立即失败，
 * 防止「登录即返回链接」或「全站默认可投稿」的回归。</p>
 */
class SoftwarePropertiesTest {

    @Test
    @DisplayName("功能开关默认关闭：enabled/publish-enabled/resolve-enabled 均为 false")
    void featureFlagsDefaultClosed() {
        SoftwareProperties properties = new SoftwareProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isPublishEnabled()).isFalse();
        assertThat(properties.isResolveEnabled()).isFalse();
    }

    @Test
    @DisplayName("发布资格默认拒绝且登录前提不可关闭（fail-closed 安全边界）")
    void publisherAndLoginInvariants() {
        SoftwareProperties properties = new SoftwareProperties();

        // 全站默认发布资格固定 false（仅后台逐用户开通）
        assertThat(properties.isPublisherDefaultEnabled()).isFalse();
        // resolve 强制登录是三种查看模式的共同前提，设计上不可配置关闭
        assertThat(properties.isResolveLoginRequired()).isTrue();
    }

    @Test
    @DisplayName("限流与容量阈值符合方案 11.2 初始产品默认值")
    void rateLimitDefaults() {
        SoftwareProperties properties = new SoftwareProperties();

        assertThat(properties.getPublishSubmissionsPerUserDay()).isEqualTo(10);
        assertThat(properties.getCommentPerUserMinute()).isEqualTo(5);
        assertThat(properties.getResolvePerUserMinute()).isEqualTo(30);
        assertThat(properties.getFeedbackPerUserDay()).isEqualTo(20);
        assertThat(properties.getRevisionsMaxReleases()).isEqualTo(50);
        assertThat(properties.getReleaseMaxSources()).isEqualTo(10);
        assertThat(properties.getScreenshotMaxCount()).isEqualTo(8);
        assertThat(properties.getSuspectedDistinctReporters()).isEqualTo(3);
    }
}

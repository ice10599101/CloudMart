package com.cloudmart.payment.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R09 渠道白名单注册表：MOCK 开关、凭据过滤、prod 启动自检 fail-closed。
 */
@DisplayName("ChannelRegistry 渠道白名单")
class ChannelRegistryTest {

    @Test
    @DisplayName("prod profile 下 MOCK 开启 → 启动自检直接失败（不允许假支付通道进生产）")
    void rejectMockInProduction_mockEnabled_throws() {
        Environment prodEnv = mock(Environment.class);
        when(prodEnv.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);
        ChannelRegistry registry = new ChannelRegistry(List.of(), true, prodEnv);

        assertThatThrownBy(registry::rejectMockInProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R09");
    }

    @Test
    @DisplayName("prod profile 下 MOCK 关闭 → 自检通过（无真实适配器时白名单为空也合法）")
    void rejectMockInProduction_mockDisabled_ok() {
        Environment prodEnv = mock(Environment.class);
        when(prodEnv.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);
        ChannelRegistry registry = new ChannelRegistry(List.of(), false, prodEnv);

        assertThatCode(registry::rejectMockInProduction).doesNotThrowAnyException();
        assertThat(registry.availableChannelCodes()).isEmpty();
        assertThat(registry.isChannelAllowed("MOCK")).isFalse();
    }

    @Test
    @DisplayName("非 prod profile MOCK 开启 → 自检通过且仅 MOCK 在白名单")
    void nonProd_mockEnabled_ok() {
        Environment devEnv = mock(Environment.class);
        when(devEnv.acceptsProfiles(Profiles.of("prod"))).thenReturn(false);
        ChannelRegistry registry = new ChannelRegistry(List.of(), true, devEnv);

        assertThatCode(registry::rejectMockInProduction).doesNotThrowAnyException();
        assertThat(registry.availableChannelCodes()).containsExactly("MOCK");
        assertThat(registry.isChannelAllowed("mock")).isTrue();
    }
}

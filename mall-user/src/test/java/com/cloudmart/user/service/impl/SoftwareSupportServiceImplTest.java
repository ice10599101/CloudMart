package com.cloudmart.user.service.impl;

import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import com.cloudmart.user.vo.software.SoftwareProfileVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 9.6 用户最小资料适配单测：仅昵称/头像/可用性；缺失 ID 不出现在结果；
 * status=1 视为账号可用。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareSupportServiceImplTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private com.cloudmart.user.service.impl.SoftwareSupportServiceImpl service;

    private UserVO user(Long id, Integer status) {
        return new UserVO(id, "xd000" + id, "昵称" + id, "user" + id + "@test.dev",
                "https://cdn.example.test/avatar/" + id + ".png", null, null, null, null,
                null, null, null, null, status, null, LocalDateTime.now());
    }

    @Test
    @DisplayName("profiles：只保留 id/nickname/avatar/accountAvailable，不泄露邮箱")
    void profiles_mapsMinimalFields() {
        when(userService.batchGetUsers(anyList()))
                .thenReturn(List.of(user(7L, 1), user(8L, 0)));

        List<SoftwareProfileVO> profiles = service.profiles(List.of(7L, 8L));

        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(0).id()).isEqualTo(7L);
        assertThat(profiles.get(0).accountAvailable()).isTrue();
        assertThat(profiles.get(1).accountAvailable()).isFalse();
        // 记录不含 email 等敏感字段（record 字段层面即不存在）
        assertThat(profiles.get(0).nickname()).isEqualTo("昵称7");
        assertThat(profiles.get(0).avatar()).contains("avatar/7");
    }

    @Test
    @DisplayName("profiles：仅返回存在的用户（缺失 ID 由调用方识别），空查询被上游拒绝")
    void profiles_missingIdsOmitted() {
        when(userService.batchGetUsers(anyList())).thenReturn(List.of(user(7L, 1)));

        List<SoftwareProfileVO> profiles = service.profiles(List.of(7L, 999L));

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).id()).isEqualTo(7L);
    }
}

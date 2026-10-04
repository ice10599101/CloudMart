package com.cloudmart.community.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.vo.UserLevelVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T09：跨域经验发放校验——稳定业务 ID 必填、来源白名单（服务端从 sourceBizId
 * 派生，不信任自报）、数额边界；唯一键 (user, source, bizId) 保证同事实只发一次。
 */
@DisplayName("InternalGrowthController 经验发放校验（T09）")
class InternalGrowthControllerTest {

    private InternalGrowthController controller;
    private GrowthService growthService;

    @BeforeEach
    void setUp() {
        growthService = mock(GrowthService.class);
        controller = new InternalGrowthController(growthService);
    }

    private Map<String, Object> body(String sourceBizId, int exp) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 42L);
        body.put("exp", exp);
        body.put("sourceBizId", sourceBizId);
        body.put("description", "每日签到");
        return body;
    }

    private void stubLevel() {
        when(growthService.getUserLevel(anyLong())).thenReturn(new UserLevelVO(
                42L, 3, 1200, 1200L, "探路者", "icon.png", 1500, "行者", 0.8));
    }

    @Test
    @DisplayName("合法发放：source 从 sourceBizId 服务端派生（WISH_SIGNIN:123:EXP → bizId=123）")
    void grantExp_valid_derivesSourceServerSide() {
        stubLevel();
        var resp = controller.grantExp(body("WISH_SIGNIN:123:EXP", 10));

        assertThat(resp.success()).isTrue();
        verify(growthService).addExp(eq(42L), eq(10), eq("WISH_SIGNIN"), eq(123L), any());
    }

    @Test
    @DisplayName("缺少 sourceBizId → 拒绝（旧实现 bizId=null，唯一键无法防重）")
    void grantExp_missingSourceBizId_rejected() {
        assertThatThrownBy(() -> controller.grantExp(body(null, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXP_GRANT_INVALID");
        verify(growthService, never()).addExp(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("来源不在白名单（自报 CHECK_IN 伪造）→ 拒绝")
    void grantExp_sourceNotWhitelisted_rejected() {
        assertThatThrownBy(() -> controller.grantExp(body("CHECK_IN:123:EXP", 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXP_GRANT_INVALID");
        assertThatThrownBy(() -> controller.grantExp(body("WISH_ADMIN:1:EXP", 10)))
                .isInstanceOf(BusinessException.class);
        verify(growthService, never()).addExp(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("数额越界（0 或 >500）→ 拒绝")
    void grantExp_expOutOfBounds_rejected() {
        assertThatThrownBy(() -> controller.grantExp(body("WISH_SIGNIN:1:EXP", 0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXP_GRANT_INVALID");
        assertThatThrownBy(() -> controller.grantExp(body("WISH_SIGNIN:1:EXP", 501)))
                .isInstanceOf(BusinessException.class);
        verify(growthService, never()).addExp(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("格式非法（非数字 ID / 段数不符 / 后缀不符）→ 拒绝")
    void grantExp_malformedSourceBizId_rejected() {
        assertThatThrownBy(() -> controller.grantExp(body("WISH_SIGNIN:abc:EXP", 10)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.grantExp(body("WISH_SIGNIN:123", 10)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.grantExp(body("WISH_SIGNIN:123:STAR", 10)))
                .isInstanceOf(BusinessException.class);
        verify(growthService, never()).addExp(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("重复发放（同 sourceBizId）由唯一键吸收：addExp 幂等跳过不抛异常")
    void grantExp_duplicate_idempotentByUniqueKey() {
        stubLevel();
        Mockito.doNothing().when(growthService).addExp(anyLong(), anyInt(), any(), anyLong(), any());

        var first = controller.grantExp(body("WISH_MILESTONE:9:EXP", 30));
        var replay = controller.grantExp(body("WISH_MILESTONE:9:EXP", 30));

        assertThat(first.success()).isTrue();
        assertThat(replay.success()).isTrue();
        verify(growthService, Mockito.times(2)).addExp(eq(42L), eq(30), eq("WISH_MILESTONE"),
                eq(9L), any());
    }
}

package com.cloudmart.job.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.handler.BusinessJobHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * OPS-01：白名单语义——已知 handler 可执行；任意类名/未注册目标拒绝。
 */
class JobInvokerTest {

    private BusinessJobHandler businessJobHandler;
    private JobInvoker jobInvoker;

    @BeforeEach
    void setUp() {
        businessJobHandler = mock(BusinessJobHandler.class);
        jobInvoker = new JobInvoker(businessJobHandler);
    }

    @Test
    @DisplayName("白名单 handler 按名执行")
    void invokeByName_whitelisted() {
        jobInvoker.invokeByName("orderTimeoutCancelHandler", "TEST");
        verify(businessJobHandler).orderTimeoutCancelHandler();
    }

    @Test
    @DisplayName("未注册目标拒绝（不反射执行任意类名）")
    void invokeByName_unregistered_rejected() {
        assertThatThrownBy(() -> jobInvoker.invokeByName("java.lang.Runtime.getRuntime", "TEST"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "JOB_HANDLER_NOT_FOUND");
    }

    @Test
    @DisplayName("handler 执行失败向调用方传播（XXL 记失败）")
    void invokeByName_failure_propagates() {
        doThrow(new IllegalStateException("下游失败"))
                .when(businessJobHandler).orderTimeoutCancelHandler();
        assertThatCode(() -> jobInvoker.invokeByName("orderTimeoutCancelHandler", "TEST"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("全部白名单 handler 均可解析（24 个 @XxlJob 对齐）")
    void registry_complete() {
        String[] handlers = {"groupExpirationHandler", "orderAutoConfirmHandler",
                "orderTimeoutCancelHandler", "couponExpirationHandler", "treeMoodScanHandler",
                "wishOverdueScanHandler", "homeHotCacheRefreshHandler", "seasonScanHandler",
                "badgeCompensationScanHandler", "capsuleOpenScanHandler", "aiReminderScanHandler",
                "leaderboardRefreshHandler", "encounterMatchHandler", "traceCleanupHandler",
                "starlightDecayHandler", "starlightReconcileHandler", "levelUpgradeHandler",
                "restrictionReleaseHandler", "riskScoreDecayHandler", "inactiveArchiveHandler",
                "dataExportPurgeHandler", "accountDeletionScanHandler", "activityRewardCheckHandler",
                "brandRewardCheckHandler"};
        for (String h : handlers) {
            assertThatCode(() -> jobInvoker.invokeByName(h, "REGISTRY")).doesNotThrowAnyException();
        }
    }
}

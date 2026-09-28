package com.cloudmart.job.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.entity.SysJob;
import com.cloudmart.job.entity.SysJobLog;
import com.cloudmart.job.handler.BusinessJobHandler;
import com.cloudmart.job.repository.SysJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * JOB-01：任务执行白名单 registry——已知目标真实执行（成功/失败写执行日志）、
 * 未知目标拒绝（不通过反射执行任意配置）。
 */
@DisplayName("JobInvoker 白名单执行")
class JobInvokerTest {

    private JobInvoker jobInvoker;
    private BusinessJobHandler businessJobHandler;
    private SysJobLogMapper sysJobLogMapper;

    @BeforeEach
    void setUp() {
        sysJobLogMapper = mock(SysJobLogMapper.class);
        businessJobHandler = mock(BusinessJobHandler.class);
        jobInvoker = new JobInvoker(sysJobLogMapper, businessJobHandler);
    }

    private SysJob job(String target) {
        SysJob job = new SysJob();
        job.setId(1L);
        job.setJobName("测试任务");
        job.setInvokeTarget(target);
        return job;
    }

    @Test
    @DisplayName("白名单内的目标真实执行 handler 并写成功日志")
    void invoke_registeredTarget_executesHandler() {
        jobInvoker.invoke(job("orderTimeoutCancelHandler"));

        verify(businessJobHandler).orderTimeoutCancelHandler();
        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(sysJobLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    @DisplayName("handler 抛异常 → 写失败日志（含异常信息），不向调度器传播")
    void invoke_handlerFails_writesFailureLog() {
        org.mockito.Mockito.doThrow(new RuntimeException("下游服务不可用"))
                .when(businessJobHandler).orderTimeoutCancelHandler();

        jobInvoker.invoke(job("orderTimeoutCancelHandler"));

        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(sysJobLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getExceptionInfo()).contains("下游服务不可用");
    }

    @Test
    @DisplayName("未知 invokeTarget 拒绝执行：写失败日志、不执行任何 handler（调度路径吞异常保调度器）")
    void invoke_unknownTarget_rejected() {
        jobInvoker.invoke(job("java.lang.Runtime.getRuntime"));

        verify(businessJobHandler, org.mockito.Mockito.never()).orderTimeoutCancelHandler();
        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(sysJobLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getExceptionInfo()).contains("未注册的任务目标");

        assertThat(jobInvoker.isRegistered("java.lang.Runtime.getRuntime")).isFalse();
    }

    @Test
    @DisplayName("isRegistered 用于创建/修改前置校验")
    void isRegistered() {
        assertThat(jobInvoker.isRegistered("couponExpirationHandler")).isTrue();
        assertThat(jobInvoker.isRegistered("evilTarget")).isFalse();
        assertThat(jobInvoker.isRegistered(null)).isFalse();
    }
}

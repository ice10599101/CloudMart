package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareErasureTask;
import com.cloudmart.software.repository.SoftwareErasureSqlMapper;
import com.cloudmart.software.repository.SoftwareErasureTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 11.3 软件域注销状态机单测：幂等 DONE、阶段顺序、失败可重试不丢进度。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareErasureServiceImplTest {

    private static final Long USER_ID = 42L;

    @Mock
    private SoftwareErasureTaskMapper taskMapper;

    @Mock
    private SoftwareErasureSqlMapper sqlMapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    @org.mockito.Spy
    private java.time.Clock clock = java.time.Clock.systemUTC();

    @InjectMocks
    private SoftwareErasureServiceImpl service;

    private SoftwareErasureTask task(String status, String phase, int attempts) {
        SoftwareErasureTask task = new SoftwareErasureTask();
        task.setId(1L);
        task.setUserId(USER_ID);
        task.setStatus(status);
        task.setPhase(phase);
        task.setCursorId(0L);
        task.setAttempts(attempts);
        task.setLeaseVersion(0);
        return task;
    }

    @SuppressWarnings("unchecked")
    private SoftwareErasureTask taskFromSelect() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task("PENDING", "", 0));
        return null;
    }

    @Test
    @DisplayName("首次注销：五个阶段顺序执行，全部成功后置 DONE")
    void eraseUser_runsAllPhasesAndDone() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task("PENDING", "", 0));
        when(sqlMapper.unlistActiveSoftware(USER_ID)).thenReturn(2);
        when(sqlMapper.softDeleteOwnComments(USER_ID)).thenReturn(5);

        boolean done = service.eraseUser(USER_ID);

        assertThat(done).isTrue();
        // 阶段顺序：软件下架 → 评论/点赞 → 收藏/获取事实 → 反馈/举报身份 → 授权/资格
        var inOrder = org.mockito.Mockito.inOrder(sqlMapper);
        inOrder.verify(sqlMapper).unlistActiveSoftware(USER_ID);
        inOrder.verify(sqlMapper).softDeleteOwnComments(USER_ID);
        inOrder.verify(sqlMapper).deleteOwnCommentLikes(USER_ID);
        inOrder.verify(sqlMapper).deleteOwnFavorites(USER_ID);
        inOrder.verify(sqlMapper).deleteOwnAccessDaily(USER_ID);
        inOrder.verify(sqlMapper).deleteOwnLinkFeedback(USER_ID);
        inOrder.verify(sqlMapper).deleteOwnReports(USER_ID);
        inOrder.verify(sqlMapper).revokeOwnAccessGrants(USER_ID);
        inOrder.verify(sqlMapper).disableOwnPublishPermission(USER_ID);

        ArgumentCaptor<SoftwareErasureTask> captor = ArgumentCaptor.forClass(SoftwareErasureTask.class);
        verify(taskMapper, times(7)).updateById(captor.capture());
        List<SoftwareErasureTask> updates = captor.getAllValues();
        assertThat(updates.get(updates.size() - 1).getStatus()).isEqualTo("DONE");
        assertThat(updates.get(updates.size() - 1).getPhase()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("DONE 后重复调用幂等返回成功，不再触碰任何清理语句")
    void eraseUser_doneIdempotent() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task("DONE", "DONE", 3));

        boolean done = service.eraseUser(USER_ID);

        assertThat(done).isTrue();
        verify(sqlMapper, never()).unlistActiveSoftware(any());
        verify(taskMapper, never()).updateById(any(SoftwareErasureTask.class));
    }

    @Test
    @DisplayName("阶段失败：置 FAILED+退避并抛 SOFTWARE_ERASURE_INCOMPLETE（可重试）")
    void eraseUser_phaseFailureRetryable() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task("PENDING", "", 0));
        when(sqlMapper.unlistActiveSoftware(USER_ID)).thenReturn(2);
        // 第二阶段（评论清理）抛错：第一阶段进度已持久化，重试从未完成阶段续跑
        doThrow(new RuntimeException("db down")).when(sqlMapper).softDeleteOwnComments(USER_ID);

        assertThatThrownBy(() -> service.eraseUser(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_ERASURE_INCOMPLETE");

        ArgumentCaptor<SoftwareErasureTask> captor = ArgumentCaptor.forClass(SoftwareErasureTask.class);
        verify(taskMapper, times(3)).updateById(captor.capture());
        SoftwareErasureTask failed = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getLastError()).contains("db down");
    }

    @Test
    @DisplayName("并发首插：唯一键冲突后取已存在行，不重复创建任务")
    void eraseUser_duplicateKeyTakesExisting() {
        when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null)
                .thenReturn(task("PENDING", "", 0));
        when(taskMapper.insert(any(SoftwareErasureTask.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_erasure_user"));

        boolean done = service.eraseUser(USER_ID);

        assertThat(done).isTrue();
        verify(taskMapper).insert(any(SoftwareErasureTask.class));
        verify(sqlMapper).unlistActiveSoftware(USER_ID);
    }

    @Test
    @DisplayName("非法 userId 直接拒绝")
    void eraseUser_rejectsInvalidUserId() {
        assertThatThrownBy(() -> service.eraseUser(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_ERASURE_INVALID");
        assertThatThrownBy(() -> service.eraseUser(0L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_ERASURE_INVALID");
        verify(taskMapper, never()).selectOne(any());
    }
}

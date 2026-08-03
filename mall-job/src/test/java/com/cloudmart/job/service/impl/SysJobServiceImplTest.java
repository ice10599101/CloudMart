package com.cloudmart.job.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.dto.SysJobLogResponse;
import com.cloudmart.job.dto.SysJobRequest;
import com.cloudmart.job.dto.SysJobResponse;
import com.cloudmart.job.entity.SysJob;
import com.cloudmart.job.entity.SysJobLog;
import com.cloudmart.job.repository.SysJobLogMapper;
import com.cloudmart.job.repository.SysJobMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysJobServiceImpl 单元测试")
class SysJobServiceImplTest {

    @Mock
    private SysJobMapper sysJobMapper;

    @Mock
    private SysJobLogMapper sysJobLogMapper;

    @Mock
    private ThreadPoolTaskScheduler taskScheduler;

    @Mock
    private JobInvoker jobInvoker;

    @InjectMocks
    private SysJobServiceImpl sysJobService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysJob.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysJobLog.class);
    }

    private SysJob buildJob() {
        SysJob job = new SysJob();
        job.setId(1L);
        job.setJobName("测试任务");
        job.setJobGroup("DEFAULT");
        job.setInvokeTarget("testTask.execute()");
        job.setCronExpression("0/10 * * * * ?");
        job.setMisfirePolicy(1);
        job.setConcurrent(1);
        job.setStatus(0);
        job.setRemark("test");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    private SysJobRequest buildRequest() {
        return new SysJobRequest("测试任务", "DEFAULT", "testTask.execute()",
                "0/10 * * * * ?", 1, 1, 0, "test");
    }

    @Nested
    @DisplayName("create 测试")
    class CreateTests {

        @Test
        @DisplayName("创建任务 - 状态为启用时自动调度")
        void shouldCreateAndScheduleJob() {
            SysJobRequest request = buildRequest();
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            when(sysJobMapper.insert(any(SysJob.class))).thenAnswer(invocation -> {
                SysJob job = invocation.getArgument(0);
                job.setId(1L);
                return 1;
            });
            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

            Long id = sysJobService.create(request);

            assertThat(id).isEqualTo(1L);
            verify(sysJobMapper).insert(any(SysJob.class));
            verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        }

        @Test
        @DisplayName("创建任务 - 状态为暂停时不调度")
        void shouldCreateWithoutScheduleWhenPaused() {
            SysJobRequest request = new SysJobRequest("暂停任务", "DEFAULT", "testTask.execute()",
                    "0/10 * * * * ?", 1, 1, 1, null);

            when(sysJobMapper.insert(any(SysJob.class))).thenAnswer(invocation -> {
                SysJob job = invocation.getArgument(0);
                job.setId(2L);
                return 1;
            });

            sysJobService.create(request);

            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
        }

        @Test
        @DisplayName("创建任务 - 无效Cron表达式时抛异常")
        void shouldThrowWhenCronInvalid() {
            SysJobRequest request = new SysJobRequest("测试任务", "DEFAULT", "testTask.execute()",
                    "invalid-cron", 1, 1, 0, null);

            assertThatThrownBy(() -> sysJobService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("CRON_INVALID");
        }
    }

    @Nested
    @DisplayName("getById 测试")
    class GetByIdTests {

        @Test
        @DisplayName("查询任务 - 成功返回")
        void shouldGetJobById() {
            SysJob job = buildJob();
            when(sysJobMapper.selectById(1L)).thenReturn(job);

            SysJobResponse response = sysJobService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.jobName()).isEqualTo("测试任务");
        }

        @Test
        @DisplayName("查询任务 - 不存在时抛异常")
        void shouldThrowWhenJobNotFound() {
            when(sysJobMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> sysJobService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("JOB_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("update 测试")
    class UpdateTests {

        @Test
        @DisplayName("更新任务 - 成功并重新调度")
        void shouldUpdateAndRescheduleJob() {
            SysJob job = buildJob();
            SysJobRequest request = buildRequest();
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            when(sysJobMapper.selectById(1L)).thenReturn(job);
            when(sysJobMapper.updateById(any(SysJob.class))).thenReturn(1);
            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

            sysJobService.update(1L, request);

            verify(sysJobMapper).updateById(any(SysJob.class));
            verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        }

        @Test
        @DisplayName("更新任务 - 不存在时抛异常")
        void shouldThrowWhenJobNotFound() {
            when(sysJobMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> sysJobService.update(999L, buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("JOB_NOT_FOUND");
        }

        @Test
        @DisplayName("更新任务 - 无效Cron表达式时抛异常")
        void shouldThrowWhenCronInvalid() {
            SysJob job = buildJob();
            SysJobRequest request = new SysJobRequest("测试任务", "DEFAULT", "testTask.execute()",
                    "invalid-cron", 1, 1, 0, null);

            when(sysJobMapper.selectById(1L)).thenReturn(job);

            assertThatThrownBy(() -> sysJobService.update(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("CRON_INVALID");
        }
    }

    @Nested
    @DisplayName("delete 测试")
    class DeleteTests {

        @Test
        @DisplayName("删除任务 - 成功删除并取消调度")
        void shouldDeleteJob() {
            sysJobService.delete(1L);

            verify(sysJobMapper).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("changeStatus 测试")
    class ChangeStatusTests {

        @Test
        @DisplayName("切换状态 - 启用时调度任务")
        void shouldScheduleWhenEnabling() {
            SysJob job = buildJob();
            job.setStatus(1);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            when(sysJobMapper.selectById(1L)).thenReturn(job);
            when(sysJobMapper.updateById(any(SysJob.class))).thenReturn(1);
            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

            sysJobService.changeStatus(1L, 0);

            verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
            verify(sysJobMapper).updateById(any(SysJob.class));
        }

        @Test
        @DisplayName("切换状态 - 暂停时取消调度")
        void shouldCancelWhenPausing() {
            SysJobRequest request = buildRequest();
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            when(sysJobMapper.insert(any(SysJob.class))).thenAnswer(invocation -> {
                SysJob job = invocation.getArgument(0);
                job.setId(1L);
                return 1;
            });
            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

            sysJobService.create(request);

            when(sysJobMapper.selectById(1L)).thenReturn(buildJob());
            when(sysJobMapper.updateById(any(SysJob.class))).thenReturn(1);

            sysJobService.changeStatus(1L, 1);

            verify(future).cancel(false);
            verify(sysJobMapper).updateById(any(SysJob.class));
        }

        @Test
        @DisplayName("切换状态 - 任务不存在时抛异常")
        void shouldThrowWhenJobNotFound() {
            when(sysJobMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> sysJobService.changeStatus(999L, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("JOB_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("runOnce 测试")
    class RunOnceTests {

        @Test
        @DisplayName("立即执行 - 成功调用JobInvoker")
        void shouldRunOnce() {
            SysJob job = buildJob();
            when(sysJobMapper.selectById(1L)).thenReturn(job);

            sysJobService.runOnce(1L);

            verify(jobInvoker).invoke(job);
        }

        @Test
        @DisplayName("立即执行 - 任务不存在时抛异常")
        void shouldThrowWhenJobNotFound() {
            when(sysJobMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> sysJobService.runOnce(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("JOB_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("page 测试")
    class PageTests {

        @Test
        @DisplayName("分页查询任务 - 成功返回")
        void shouldPageJobs() {
            when(sysJobMapper.selectPage(any(), any())).thenReturn(
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0)
            );

            IPage<SysJobResponse> result = sysJobService.page(1, 10, null, null);

            assertThat(result).isNotNull();
            verify(sysJobMapper).selectPage(any(), any());
        }
    }

    @Nested
    @DisplayName("pageJobLogs 测试")
    class PageJobLogsTests {

        @Test
        @DisplayName("分页查询任务日志 - 无jobId时返回全部")
        void shouldPageJobLogsWithoutJobId() {
            when(sysJobLogMapper.selectPage(any(), any())).thenReturn(
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0)
            );

            IPage<SysJobLogResponse> result = sysJobService.pageJobLogs(null, 1, 10);

            assertThat(result).isNotNull();
            verify(sysJobLogMapper).selectPage(any(), any());
        }

        @Test
        @DisplayName("分页查询任务日志 - 按jobId过滤")
        void shouldPageJobLogsWithJobId() {
            SysJob job = buildJob();
            when(sysJobMapper.selectById(1L)).thenReturn(job);
            when(sysJobLogMapper.selectPage(any(), any())).thenReturn(
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0)
            );

            IPage<SysJobLogResponse> result = sysJobService.pageJobLogs(1L, 1, 10);

            assertThat(result).isNotNull();
            verify(sysJobMapper).selectById(1L);
            verify(sysJobLogMapper).selectPage(any(), any());
        }
    }

    @Nested
    @DisplayName("deleteJobLog 测试")
    class DeleteJobLogTests {

        @Test
        @DisplayName("删除任务日志 - 成功")
        void shouldDeleteJobLog() {
            sysJobService.deleteJobLog(1L);

            verify(sysJobLogMapper).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("cleanJobLogs 测试")
    class CleanJobLogsTests {

        @Test
        @DisplayName("清空任务日志 - 成功")
        void shouldCleanJobLogs() {
            when(sysJobLogMapper.delete(any())).thenReturn(0);

            sysJobService.cleanJobLogs();

            verify(sysJobLogMapper).delete(any());
        }
    }
}

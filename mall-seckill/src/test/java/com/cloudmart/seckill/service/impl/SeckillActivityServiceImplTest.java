package com.cloudmart.seckill.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeckillActivityServiceImpl 单元测试")
class SeckillActivityServiceImplTest {

    @Mock
    private SeckillActivityMapper activityMapper;

    @Mock
    private SeckillConverter seckillConverter;

    @InjectMocks
    private SeckillActivityServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime START = NOW.plusDays(1);
    private static final LocalDateTime END = NOW.plusDays(2);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SeckillActivity.class);
    }

    private SeckillActivity buildActivity(Long id, String name, String status) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setName(name);
        activity.setStatus(status);
        activity.setStartTime(START);
        activity.setEndTime(END);
        activity.setCreatedAt(NOW);
        return activity;
    }

    private SeckillActivityDTO buildActivityDTO(SeckillActivity activity) {
        return new SeckillActivityDTO(
                activity.getId(), activity.getName(), "desc",
                activity.getStartTime(), activity.getEndTime(),
                activity.getStatus(), activity.getCreatedAt()
        );
    }

    @Nested
    @DisplayName("createActivity 方法")
    class CreateActivityTest {

        @Test
        @DisplayName("正常创建活动 - 成功")
        void shouldCreateActivitySuccessfully() {
            CreateActivityRequest request = new CreateActivityRequest("闪购", "desc", START, END);
            SeckillActivity entity = buildActivity(null, "闪购", "UPCOMING");
            SeckillActivityDTO dto = buildActivityDTO(entity);

            when(seckillConverter.toEntity(request)).thenReturn(entity);
            when(seckillConverter.toActivityDTO(entity)).thenReturn(dto);

            SeckillActivityDTO result = service.createActivity(request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("闪购");
            assertThat(entity.getStatus()).isEqualTo("UPCOMING");
            verify(activityMapper).insert(any(SeckillActivity.class));
        }

        @Test
        @DisplayName("结束时间不晚于开始时间 - 抛出异常")
        void shouldThrowWhenEndTimeNotAfterStartTime() {
            CreateActivityRequest request = new CreateActivityRequest("闪购", "desc", END, START);

            assertThatThrownBy(() -> service.createActivity(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_TIME_RANGE"));
        }

        @Test
        @DisplayName("结束时间等于开始时间 - 抛出异常")
        void shouldThrowWhenEndTimeEqualsStartTime() {
            CreateActivityRequest request = new CreateActivityRequest("闪购", "desc", START, START);

            assertThatThrownBy(() -> service.createActivity(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getActivity 方法")
    class GetActivityTest {

        @Test
        @DisplayName("活动存在 - 返回DTO")
        void shouldReturnActivityWhenExists() {
            SeckillActivity activity = buildActivity(1L, "闪购", "UPCOMING");
            SeckillActivityDTO dto = buildActivityDTO(activity);

            when(activityMapper.selectById(1L)).thenReturn(activity);
            when(seckillConverter.toActivityDTO(activity)).thenReturn(dto);

            SeckillActivityDTO result = service.getActivity(1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("活动不存在 - 抛出异常")
        void shouldThrowWhenActivityNotFound() {
            when(activityMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.getActivity(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACTIVITY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("listActivities 方法")
    class ListActivitiesTest {

        @Test
        @DisplayName("按状态筛选 - 返回匹配列表")
        void shouldListByStatus() {
            SeckillActivity activity = buildActivity(1L, "闪购", "UPCOMING");
            SeckillActivityDTO dto = buildActivityDTO(activity);

            when(activityMapper.selectList(any())).thenReturn(List.of(activity));
            when(seckillConverter.toActivityDTOList(List.of(activity))).thenReturn(List.of(dto));

            List<SeckillActivityDTO> result = service.listActivities("UPCOMING");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().status()).isEqualTo("UPCOMING");
        }

        @Test
        @DisplayName("不筛选状态 - 返回全部列表")
        void shouldListAllWhenStatusIsNull() {
            SeckillActivity a1 = buildActivity(1L, "闪购1", "UPCOMING");
            SeckillActivity a2 = buildActivity(2L, "闪购2", "ONGOING");

            when(activityMapper.selectList(any())).thenReturn(List.of(a1, a2));
            when(seckillConverter.toActivityDTOList(List.of(a1, a2)))
                    .thenReturn(List.of(buildActivityDTO(a1), buildActivityDTO(a2)));

            List<SeckillActivityDTO> result = service.listActivities(null);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("updateActivityStatus 方法")
    class UpdateActivityStatusTest {

        @Test
        @DisplayName("活动存在 - 更新状态成功")
        void shouldUpdateStatusSuccessfully() {
            SeckillActivity activity = buildActivity(1L, "闪购", "UPCOMING");
            SeckillActivityDTO dto = buildActivityDTO(activity);

            when(activityMapper.selectById(1L)).thenReturn(activity);
            when(seckillConverter.toActivityDTO(activity)).thenReturn(dto);

            SeckillActivityDTO result = service.updateActivityStatus(1L, "ONGOING");

            assertThat(activity.getStatus()).isEqualTo("ONGOING");
            verify(activityMapper).updateById(activity);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("活动不存在 - 抛出异常")
        void shouldThrowWhenActivityNotFoundOnUpdate() {
            when(activityMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.updateActivityStatus(999L, "ONGOING"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACTIVITY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("refreshActivityStatuses 方法")
    class RefreshActivityStatusesTest {

        @Test
        @DisplayName("将 UPCOMING 刷新为 ONGOING，ONGOING 刷新为 ENDED")
        void shouldRefreshStatuses() {
            SeckillActivity upcoming = buildActivity(1L, "即将开始", "UPCOMING");
            SeckillActivity ongoing = buildActivity(2L, "进行中", "ONGOING");

            when(activityMapper.selectList(any()))
                    .thenReturn(List.of(upcoming))
                    .thenReturn(List.of(ongoing));

            service.refreshActivityStatuses();

            assertThat(upcoming.getStatus()).isEqualTo("ONGOING");
            assertThat(ongoing.getStatus()).isEqualTo("ENDED");
            verify(activityMapper).updateById(upcoming);
            verify(activityMapper).updateById(ongoing);
        }
    }
}

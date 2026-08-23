package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.entity.WishCheckin;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.enums.GrowthRecordType;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishCheckinMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.vo.AnnualReportVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnualReportServiceImpl 单元测试")
class AnnualReportServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishCheckinMapper checkinMapper;
    @Mock
    private WishGrowthRecordMapper growthRecordMapper;
    @Mock
    private WishCategoryMapper categoryMapper;
    @Mock
    private AnnualReportGenerator reportGenerator;

    private AnnualReportServiceImpl reportService;

    private static final Long USER_ID = 1001L;
    private static final int YEAR = LocalDate.now().getYear();

    @BeforeAll
    static void initEntityMeta() {
        // LambdaQueryWrapper 构造期解析列名需要 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Wish.class);
        TableInfoHelper.initTableInfo(assistant, WishCheckin.class);
        TableInfoHelper.initTableInfo(assistant, WishGrowthRecord.class);
    }

    @BeforeEach
    void setUp() {
        reportService = new AnnualReportServiceImpl(
                wishMapper, checkinMapper, growthRecordMapper, categoryMapper, reportGenerator);
    }

    private AnnualReportVO cachedReport() {
        return new AnnualReportVO(YEAR, 8, 120, "AI 生成的成长总结",
                List.of(new AnnualReportVO.Milestone(LocalDate.of(YEAR, 6, 1), "记下了一段成长", "内容")),
                List.of(new AnnualReportVO.TopCategory("健康", 5)));
    }

    @Nested
    @DisplayName("年份校验")
    class YearValidation {

        @Test
        @DisplayName("晚于当前年 → 400 WISH_VALIDATION_ERROR")
        void rejectFutureYear() {
            assertThatThrownBy(() -> reportService.getOrGenerateReport(USER_ID, YEAR + 1))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("早于 2020 → 400 WISH_VALIDATION_ERROR")
        void rejectAncientYear() {
            assertThatThrownBy(() -> reportService.getOrGenerateReport(USER_ID, 2019))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("缓存路径")
    class CachePath {

        @Test
        @DisplayName("缓存命中 → 直接返回 AI 完整版，不触发聚合与任务")
        void returnCachedReportWithoutAggregation() {
            AnnualReportVO cached = cachedReport();
            when(reportGenerator.readCache(USER_ID, YEAR)).thenReturn(cached);

            AnnualReportVO result = reportService.getOrGenerateReport(USER_ID, YEAR);

            assertThat(result).isEqualTo(cached);
            verify(wishMapper, never()).selectCount(any());
            verify(reportGenerator, never()).generateAsync(anyLong(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("同步聚合")
    class Aggregation {

        @Test
        @DisplayName("有数据 → 聚合统计返回模板文案并提交异步 AI 任务")
        void aggregateAndSubmitAsyncTask() {
            when(reportGenerator.readCache(USER_ID, YEAR)).thenReturn(null);
            when(reportGenerator.tryLock(USER_ID, YEAR)).thenReturn(true);
            when(wishMapper.selectCount(any())).thenReturn(8L);
            when(checkinMapper.selectList(any())).thenReturn(List.of(
                    buildCheckin(LocalDate.of(YEAR, 1, 1)),
                    buildCheckin(LocalDate.of(YEAR, 1, 1)),
                    buildCheckin(LocalDate.of(YEAR, 2, 1))));
            when(growthRecordMapper.selectList(any())).thenReturn(List.of(buildGrowthRecord()));
            when(wishMapper.selectList(any())).thenReturn(List.of(buildWish(10L), buildWish(10L), buildWish(20L)));
            when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(
                    buildCategory(10L, "健康"), buildCategory(20L, "学习")));

            AnnualReportVO result = reportService.getOrGenerateReport(USER_ID, YEAR);

            assertThat(result.year()).isEqualTo(YEAR);
            assertThat(result.fulfilledCount()).isEqualTo(8);
            // 打卡去重：3 条记录 2 个不同日期
            assertThat(result.totalCheckinDays()).isEqualTo(2);
            // 模板降级文案（AI 版尚未生成）
            assertThat(result.growthSummary()).contains(String.valueOf(YEAR));
            assertThat(result.topCategories())
                    .containsExactly(new AnnualReportVO.TopCategory("健康", 2),
                            new AnnualReportVO.TopCategory("学习", 1));
            assertThat(result.milestones()).hasSize(1);
            verify(reportGenerator).generateAsync(eq(USER_ID), eq(YEAR), any(AnnualReportVO.class));
        }

        @Test
        @DisplayName("全年无数据 → 不提交 AI 任务，返回空报告模板文案")
        void skipAiTaskWhenNoData() {
            when(reportGenerator.readCache(USER_ID, YEAR)).thenReturn(null);
            when(wishMapper.selectCount(any())).thenReturn(0L);
            when(checkinMapper.selectList(any())).thenReturn(List.of());
            when(growthRecordMapper.selectList(any())).thenReturn(List.of());
            when(wishMapper.selectList(any())).thenReturn(List.of());

            AnnualReportVO result = reportService.getOrGenerateReport(USER_ID, YEAR);

            assertThat(result.fulfilledCount()).isZero();
            assertThat(result.totalCheckinDays()).isZero();
            assertThat(result.milestones()).isEmpty();
            assertThat(result.topCategories()).isEmpty();
            assertThat(result.growthSummary()).contains("刚刚启程");
            verify(reportGenerator, never()).generateAsync(anyLong(), anyInt(), any());
        }

        @Test
        @DisplayName("任务锁被占 → 不重复提交，仍返回统计报告")
        void skipSubmissionWhenLocked() {
            when(reportGenerator.readCache(USER_ID, YEAR)).thenReturn(null);
            when(reportGenerator.tryLock(USER_ID, YEAR)).thenReturn(false);
            when(wishMapper.selectCount(any())).thenReturn(5L);
            when(checkinMapper.selectList(any())).thenReturn(List.of(buildCheckin(LocalDate.of(YEAR, 3, 1))));
            when(growthRecordMapper.selectList(any())).thenReturn(List.of());
            when(wishMapper.selectList(any())).thenReturn(List.of());

            AnnualReportVO result = reportService.getOrGenerateReport(USER_ID, YEAR);

            assertThat(result.fulfilledCount()).isEqualTo(5);
            verify(reportGenerator, never()).generateAsync(anyLong(), anyInt(), any());
        }

        @Test
        @DisplayName("成长记录 description 超 100 字截断")
        void truncateLongDescription() {
            when(reportGenerator.readCache(USER_ID, YEAR)).thenReturn(null);
            when(wishMapper.selectCount(any())).thenReturn(1L);
            when(checkinMapper.selectList(any())).thenReturn(List.of(buildCheckin(LocalDate.of(YEAR, 3, 1))));
            WishGrowthRecord record = buildGrowthRecord();
            record.setContent("长".repeat(150));
            when(growthRecordMapper.selectList(any())).thenReturn(List.of(record));
            when(wishMapper.selectList(any())).thenReturn(List.of());

            AnnualReportVO result = reportService.getOrGenerateReport(USER_ID, YEAR);

            assertThat(result.milestones().getFirst().description()).hasSize(101).endsWith("…");
        }
    }

    private WishCheckin buildCheckin(LocalDate date) {
        WishCheckin checkin = new WishCheckin();
        checkin.setUserId(USER_ID);
        checkin.setCheckinDate(date);
        return checkin;
    }

    private WishGrowthRecord buildGrowthRecord() {
        WishGrowthRecord record = new WishGrowthRecord();
        record.setUserId(USER_ID);
        record.setType(GrowthRecordType.DIARY);
        record.setContent("今天坚持完成了晨跑");
        record.setCreatedAt(LocalDateTime.of(YEAR, 6, 1, 10, 0));
        return record;
    }

    private Wish buildWish(Long categoryId) {
        Wish wish = new Wish();
        wish.setUserId(USER_ID);
        wish.setCategoryId(categoryId);
        return wish;
    }

    private WishCategory buildCategory(Long id, String name) {
        WishCategory category = new WishCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }
}

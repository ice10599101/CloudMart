package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminBottleListQuery;
import com.cloudmart.wish.entity.DriftBottle;
import com.cloudmart.wish.entity.DriftBottleComment;
import com.cloudmart.wish.entity.DriftBottleFishLog;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.DriftBottleStatus;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.DriftBottleCommentMapper;
import com.cloudmart.wish.repository.DriftBottleFishLogMapper;
import com.cloudmart.wish.repository.DriftBottleMapper;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDriftBottleServiceImpl 单元测试")
class AdminDriftBottleServiceImplTest {

    @Mock
    private DriftBottleMapper bottleMapper;
    @Mock
    private DriftBottleCommentMapper commentMapper;
    @Mock
    private DriftBottleFishLogMapper fishLogMapper;
    @Mock
    private UserFeignClient userFeignClient;

    private AdminDriftBottleServiceImpl adminDriftBottleService;

    private static final Long USER_ID = 1001L;
    private static final Long BOTTLE_ID = 2001L;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DriftBottle.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottleComment.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottleFishLog.class);
        TableInfoHelper.initTableInfo(assistant, Wish.class);
    }

    @BeforeEach
    void setUp() {
        adminDriftBottleService = new AdminDriftBottleServiceImpl(
                bottleMapper, commentMapper, fishLogMapper, userFeignClient);
    }

    private DriftBottle buildBottle(DriftBottleStatus status) {
        DriftBottle bottle = new DriftBottle();
        bottle.setId(BOTTLE_ID);
        bottle.setThrowerUserId(USER_ID);
        bottle.setContent("<p>心事</p>");
        bottle.setStatus(status);
        bottle.setThrownAt(LocalDateTime.now(ZoneId.of("UTC")).minusHours(2));
        bottle.setPickerUserId(1003L);
        bottle.setPickedAt(LocalDateTime.now(ZoneId.of("UTC")).minusHours(1));
        bottle.setPickerIsAnonymous(true);
        bottle.setIsCollected(false);
        bottle.setReturnCount(1);
        bottle.setIsHidden(false);
        return bottle;
    }

    private Map<String, Object> buildUserMap(Long id, String nickname) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", id);
        user.put("nickname", nickname);
        return user;
    }

    // ========== listBottles ==========

    @Nested
    @DisplayName("listBottles - 管理列表")
    class ListBottlesTests {

        @Test
        @DisplayName("分页映射：真实身份/评论数/状态透出")
        void listBottles_mapsAdminVo() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.PICKED);
            Page<DriftBottle> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(bottle));
            when(bottleMapper.selectPage(any(), any())).thenReturn(page);
            when(commentMapper.selectMaps(any())).thenReturn(
                    List.of(Map.of("bottle_id", BOTTLE_ID, "cnt", 5L)));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            buildUserMap(USER_ID, "投瓶旅人"),
                            buildUserMap(1003L, "捞瓶旅人"))));

            var result = adminDriftBottleService.listBottles(new AdminBottleListQuery(null, null, null, 1, 20));

            assertThat(result.getTotal()).isEqualTo(1);
            var vo = result.getRecords().get(0);
            assertThat(vo.id()).isEqualTo(BOTTLE_ID);
            assertThat(vo.status()).isEqualTo("PICKED");
            assertThat(vo.throwerUserId()).isEqualTo(USER_ID);
            assertThat(vo.throwerNickname()).isEqualTo("投瓶旅人");
            assertThat(vo.pickerUserId()).isEqualTo(1003L);
            assertThat(vo.pickerNickname()).isEqualTo("捞瓶旅人");
            assertThat(vo.commentCount()).isEqualTo(5L);
            assertThat(vo.returnCount()).isEqualTo(1);
            assertThat(vo.isHidden()).isFalse();
        }

        @Test
        @DisplayName("Feign 降级：昵称占位不影响列表返回（Fail Open）")
        void listBottles_feignFail_fallbackNickname() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.FLOATING);
            bottle.setPickerUserId(null);
            Page<DriftBottle> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(bottle));
            when(bottleMapper.selectPage(any(), any())).thenReturn(page);
            when(commentMapper.selectMaps(any())).thenReturn(List.of());
            when(userFeignClient.batchGetUsers(any()))
                    .thenThrow(new RuntimeException("feign down"));

            var result = adminDriftBottleService.listBottles(new AdminBottleListQuery(null, null, null, 1, 20));

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).throwerNickname()).isEqualTo("心愿旅人");
            assertThat(result.getRecords().get(0).pickerNickname()).isNull();
        }
    }

    // ========== detail / updateHidden ==========

    @Nested
    @DisplayName("detail / updateHidden - 详情与下架")
    class DetailHiddenTests {

        @Test
        @DisplayName("详情：瓶子 + 评论全量（真实身份）")
        void detail_success() {
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(buildBottle(DriftBottleStatus.PICKED));
            DriftBottleComment comment = new DriftBottleComment();
            comment.setId(1L);
            comment.setBottleId(BOTTLE_ID);
            comment.setUserId(USER_ID);
            comment.setParentId(null);
            comment.setContent("你好");
            comment.setIsAnonymous(true);
            comment.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
            when(commentMapper.selectList(any())).thenReturn(List.of(comment));
            when(commentMapper.selectMaps(any())).thenReturn(List.of());
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(buildUserMap(USER_ID, "投瓶旅人"))));

            var detail = adminDriftBottleService.detail(BOTTLE_ID);

            assertThat(detail.bottle().id()).isEqualTo(BOTTLE_ID);
            assertThat(detail.comments()).hasSize(1);
            assertThat(detail.comments().get(0).userId()).isEqualTo(USER_ID);
            assertThat(detail.comments().get(0).nickname()).isEqualTo("投瓶旅人");
        }

        @Test
        @DisplayName("详情：瓶子不存在 → WISH_NOT_FOUND")
        void detail_notFound() {
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminDriftBottleService.detail(BOTTLE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }

        @Test
        @DisplayName("下架：isHidden 置 true 后返回")
        void updateHidden_success() {
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(buildBottle(DriftBottleStatus.FLOATING));
            when(commentMapper.selectMaps(any())).thenReturn(List.of());

            var vo = adminDriftBottleService.updateHidden(BOTTLE_ID, true);

            assertThat(vo.isHidden()).isTrue();
            verifyUpdateCalled();
        }

        @Test
        @DisplayName("下架：瓶子不存在 → WISH_NOT_FOUND")
        void updateHidden_notFound() {
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminDriftBottleService.updateHidden(BOTTLE_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }

        private void verifyUpdateCalled() {
            org.mockito.Mockito.verify(bottleMapper).update(any(), any());
        }
    }

    // ========== dashboard ==========

    @Nested
    @DisplayName("dashboard - 数据看板")
    class DashboardTests {

        @Test
        @DisplayName("聚合：状态分布/今日活动/14 天趋势/投瓶榜")
        void dashboard_aggregates() {
            LocalDate today = LocalDate.now(ZoneId.of("UTC"));
            String yesterday = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // bottleMapper.selectMaps 依次被调用：状态分布、投瓶趋势、投瓶榜
            when(bottleMapper.selectMaps(any())).thenReturn(
                    List.of(
                            Map.of("status", "FLOATING", "cnt", 2L),
                            Map.of("status", "PICKED", "cnt", 1L)),
                    List.of(Map.of("day", yesterday, "cnt", 3L)),
                    List.of(Map.of("uid", USER_ID, "cnt", 5L)));
            // fishLogMapper.selectMaps：打捞趋势
            when(fishLogMapper.selectMaps(any())).thenReturn(
                    List.of(Map.of("day", yesterday, "cnt", 4L)));
            // bottleMapper.selectCount 依次：隐藏数、收藏数、有评论数、今日投瓶
            when(bottleMapper.selectCount(any())).thenReturn(1L, 2L, 3L, 4L);
            when(fishLogMapper.selectCount(any())).thenReturn(7L);
            when(commentMapper.selectCount(any())).thenReturn(6L);
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(buildUserMap(USER_ID, "投瓶旅人"))));

            var dashboard = adminDriftBottleService.dashboard();

            assertThat(dashboard.totalBottles()).isEqualTo(3);
            assertThat(dashboard.floatingCount()).isEqualTo(2);
            assertThat(dashboard.pickedCount()).isEqualTo(1);
            assertThat(dashboard.returnedCount()).isZero();
            assertThat(dashboard.hiddenCount()).isEqualTo(1);
            assertThat(dashboard.collectedCount()).isEqualTo(2);
            assertThat(dashboard.repliedCount()).isEqualTo(3);
            assertThat(dashboard.todayThrowCount()).isEqualTo(4);
            assertThat(dashboard.todayFishCount()).isEqualTo(7);
            assertThat(dashboard.todayCommentCount()).isEqualTo(6);

            assertThat(dashboard.trend()).hasSize(14);
            var yesterdayTrend = dashboard.trend().stream()
                    .filter(d -> d.date().equals(yesterday))
                    .findFirst().orElseThrow();
            assertThat(yesterdayTrend.throwCount()).isEqualTo(3);
            assertThat(yesterdayTrend.fishCount()).isEqualTo(4);

            assertThat(dashboard.topThrowers()).hasSize(1);
            assertThat(dashboard.topThrowers().get(0).userId()).isEqualTo(USER_ID);
            assertThat(dashboard.topThrowers().get(0).nickname()).isEqualTo("投瓶旅人");
            assertThat(dashboard.topThrowers().get(0).throwCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("空数据：各计数为零、趋势 14 天补零")
        void dashboard_empty() {
            when(bottleMapper.selectMaps(any())).thenReturn(
                    List.of(), List.of(), List.of());
            when(fishLogMapper.selectMaps(any())).thenReturn(List.of());
            when(bottleMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);
            when(fishLogMapper.selectCount(any())).thenReturn(0L);
            when(commentMapper.selectCount(any())).thenReturn(0L);

            var dashboard = adminDriftBottleService.dashboard();

            assertThat(dashboard.totalBottles()).isZero();
            assertThat(dashboard.trend()).hasSize(14);
            assertThat(dashboard.trend()).allSatisfy(d -> {
                assertThat(d.throwCount()).isZero();
                assertThat(d.fishCount()).isZero();
            });
            assertThat(dashboard.topThrowers()).isEmpty();
        }
    }
}

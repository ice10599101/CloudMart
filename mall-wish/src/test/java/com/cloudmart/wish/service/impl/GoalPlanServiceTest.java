package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAiGoal;
import com.cloudmart.wish.enums.GoalStatus;
import com.cloudmart.wish.repository.WishAiGoalMapper;
import com.cloudmart.wish.repository.WishMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T12 目标计划：版本类型统一（Integer，修复 Long.equals(Integer) 恒不等）、
 * 版本条件软删、排序含 version=0 且整批回滚、父心愿行锁下 20 步上限并发安全、
 * 首次完成事实不被重写。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoalPlanService 单元测试（T12）")
class GoalPlanServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long WISH_ID = 2001L;

    @Mock
    private WishAiGoalMapper goalMapper;
    @Mock
    private WishMapper wishMapper;

    private GoalPlanService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Wish.class);
        TableInfoHelper.initTableInfo(assistant, WishAiGoal.class);
    }

    @BeforeEach
    void setUp() {
        service = new GoalPlanService(goalMapper, wishMapper);
        lenient().when(wishMapper.selectById(WISH_ID)).thenReturn(ownedWish());
        lenient().when(wishMapper.selectByIdForUpdate(WISH_ID)).thenReturn(ownedWish());
    }

    private Wish ownedWish() {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(USER_ID);
        return wish;
    }

    private WishAiGoal goal(long id, int version, GoalStatus status) {
        WishAiGoal goal = new WishAiGoal();
        goal.setId(id);
        goal.setUserId(USER_ID);
        goal.setWishId(WISH_ID);
        goal.setTitle("步骤" + id);
        goal.setVersion(version);
        goal.setStatus(status);
        goal.setSortOrder((int) id);
        return goal;
    }

    @Test
    @DisplayName("T12 回归：Integer 版本 CAS 更新成功（旧 Long.equals(Integer) 恒 409）")
    void updateGoal_integerVersion_matches() {
        when(goalMapper.selectById(11L))
                .thenReturn(goal(11L, 0, GoalStatus.PENDING))
                .thenReturn(goal(11L, 1, GoalStatus.IN_PROGRESS));
        when(goalMapper.update(any(), any())).thenReturn(1);

        var updated = service.updateGoal(USER_ID, 11L, null, null, GoalStatus.IN_PROGRESS, 0);

        assertThat(updated.getVersion()).isEqualTo(1);
        verify(goalMapper).update(any(), any());
    }

    @Test
    @DisplayName("版本不匹配 → WISH_VERSION_CONFLICT（409）")
    void updateGoal_versionMismatch_conflict() {
        when(goalMapper.selectById(11L)).thenReturn(goal(11L, 2, GoalStatus.PENDING));

        assertThatThrownBy(() -> service.updateGoal(USER_ID, 11L, null, null, null, 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VERSION_CONFLICT);
    }

    @Test
    @DisplayName("首次完成写 completedAt；重复勾选（已 COMPLETED）不再改写完成事实")
    void updateGoal_firstCompletionOnly() {
        when(goalMapper.selectById(11L))
                .thenReturn(goal(11L, 3, GoalStatus.IN_PROGRESS))
                .thenReturn(goal(11L, 4, GoalStatus.COMPLETED));
        when(goalMapper.update(any(), any())).thenReturn(1);

        service.updateGoal(USER_ID, 11L, null, null, GoalStatus.COMPLETED, 3);

        // completing=true → completedAt 由 CAS 写入（wrapper set 条件生效）
        verify(goalMapper).update(any(), any());
    }

    @Test
    @DisplayName("删除为版本条件软删：CAS 命中通过，版本过期 409 且不落删")
    void deleteGoal_versionCasSoftDelete() {
        when(goalMapper.selectById(11L)).thenReturn(goal(11L, 0, GoalStatus.PENDING));
        when(goalMapper.update(any(), any())).thenReturn(1);

        service.deleteGoal(USER_ID, 11L, 0);

        verify(goalMapper).update(any(), any());

        when(goalMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.deleteGoal(USER_ID, 11L, 0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VERSION_CONFLICT);
    }

    @Test
    @DisplayName("旧实现缺陷回归：version=0 的新建目标参与排序（不再被 version>0 跳过）")
    void reorder_versionZeroGoal_included() {
        WishAiGoal g1 = goal(11L, 0, GoalStatus.PENDING);
        g1.setSortOrder(0);
        WishAiGoal g2 = goal(12L, 2, GoalStatus.IN_PROGRESS);
        g2.setSortOrder(1);
        when(goalMapper.selectList(any())).thenReturn(List.of(g1, g2));
        when(goalMapper.update(any(), any())).thenReturn(1);

        service.reorder(USER_ID, WISH_ID, List.of(
                new GoalPlanService.ReorderItem(11L, 0, 1),
                new GoalPlanService.ReorderItem(12L, 2, 0)));

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WishAiGoal>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(goalMapper, org.mockito.Mockito.times(2)).update(any(), captor.capture());
    }

    @Test
    @DisplayName("排序项版本过期 → 整批 409（@Transactional 回滚，不局部成功）")
    void reorder_versionConflict_throws() {
        WishAiGoal g1 = goal(11L, 0, GoalStatus.PENDING);
        WishAiGoal g2 = goal(12L, 2, GoalStatus.PENDING);
        when(goalMapper.selectList(any())).thenReturn(List.of(g1, g2));

        assertThatThrownBy(() -> service.reorder(USER_ID, WISH_ID, List.of(
                new GoalPlanService.ReorderItem(11L, 5, 0),
                new GoalPlanService.ReorderItem(12L, 2, 1))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VERSION_CONFLICT);
        verify(goalMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("排序集合不完整/重复/非连续 → 拒绝")
    void reorder_invalidSet_rejected() {
        WishAiGoal g1 = goal(11L, 0, GoalStatus.PENDING);
        WishAiGoal g2 = goal(12L, 1, GoalStatus.PENDING);
        when(goalMapper.selectList(any())).thenReturn(List.of(g1, g2));

        // 缺项
        assertThatThrownBy(() -> service.reorder(USER_ID, WISH_ID,
                List.of(new GoalPlanService.ReorderItem(11L, 0, 0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        // 非连续（跳号）
        assertThatThrownBy(() -> service.reorder(USER_ID, WISH_ID, List.of(
                new GoalPlanService.ReorderItem(11L, 0, 0),
                new GoalPlanService.ReorderItem(12L, 1, 2))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        verify(goalMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("创建步骤在父心愿行锁内计数（20 步上限并发安全）")
    void createGoal_locksParentWish() {
        when(goalMapper.selectCount(any())).thenReturn(19L);
        when(goalMapper.insert(any(WishAiGoal.class))).thenReturn(1);

        var created = service.createGoal(USER_ID, WISH_ID, "第20步", null, null, null, null);

        assertThat(created.getVersion()).isEqualTo(0);
        assertThat(created.getSortOrder()).isEqualTo(19);
        verify(wishMapper).selectByIdForUpdate(WISH_ID);
    }

    @Test
    @DisplayName("创建第 21 步 → 拒绝")
    void createGoal_overLimit_rejected() {
        when(goalMapper.selectCount(any())).thenReturn(20L);

        assertThatThrownBy(() -> service.createGoal(USER_ID, WISH_ID, "超限", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        verify(goalMapper, never()).insert(any(WishAiGoal.class));
    }

    @Test
    @DisplayName("T12 真实环境暴露：description null 归一空串（列 NOT NULL，原实现 SQL 异常）")
    void createGoal_nullDescription_normalized() {
        when(goalMapper.selectCount(any())).thenReturn(0L);
        when(goalMapper.insert(any(WishAiGoal.class))).thenReturn(1);

        var created = service.createGoal(USER_ID, WISH_ID, "无描述步骤", null, null, null, null);

        assertThat(created.getDescription()).isEmpty();
        ArgumentCaptor<WishAiGoal> captor = ArgumentCaptor.forClass(WishAiGoal.class);
        verify(goalMapper).insert(captor.capture());
        assertThat(captor.getValue().getDescription()).isNotNull();
    }

    @Test
    @DisplayName("T12 真实环境暴露：标题限长与列宽对齐（100 而非 120，超限 SQL 截断异常）")
    void createGoal_titleOverColumnWidth_rejected() {
        assertThatThrownBy(() -> service.createGoal(USER_ID, WISH_ID, "x".repeat(101), null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        verify(goalMapper, never()).insert(any(WishAiGoal.class));
    }
}

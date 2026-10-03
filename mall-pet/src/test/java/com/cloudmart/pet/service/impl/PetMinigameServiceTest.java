package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetMinigameOperation;
import com.cloudmart.pet.entity.PetMinigameRound;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetMinigameOperationMapper;
import com.cloudmart.pet.repository.PetMinigameRoundMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * R11 小游戏时窗与操作事实测试：窗口唯一事实（uk 收敛并发/重放）、
 * 服务端时间窗判定（点击次数不参与）、结算以事实计数为权威、
 * LambdaUpdateWrapper 限定列更新不触碰终态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("小游戏时窗与操作事实测试（R11）")
class PetMinigameServiceTest {

    @Mock
    private PetMapper petMapper;
    @Mock
    private PetMinigameRoundMapper minigameMapper;
    @Mock
    private PetMinigameOperationMapper operationMapper;
    @Mock
    private PetCustodyRecordMapper custodyMapper;
    @Mock
    private PetQuotaService quotaService;

    private PetMinigameService minigameService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetMinigameRound.class);
        TableInfoHelper.initTableInfo(assistant, PetMinigameOperation.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties properties = new PetProperties();
        PetClock clock = new PetClock(java.time.Clock.systemUTC(), properties);
        minigameService = new PetMinigameService(
                petMapper, clock, quotaService,
                minigameMapper, operationMapper,
                properties,
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetUserGuardService.class),
                org.mockito.Mockito.mock(PetActivityMutex.class),
                org.mockito.Mockito.mock(PetStateService.class),
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetIntimacyService.class));
        lenientStub();
    }

    private void lenientStub() {
        org.mockito.Mockito.lenient().when(operationMapper.insert(any(PetMinigameOperation.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(minigameMapper.update(any(), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(operationMapper.selectCount(any())).thenReturn(0L);
    }

    private PetMinigameRound activeRound() {
        PetMinigameRound round = new PetMinigameRound();
        round.setId(77L);
        round.setUserId(100L);
        round.setPetId(1L);
        round.setGameType("CATCH");
        round.setStatus("ACTIVE");
        round.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        round.setDeadlineAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(30));
        // 全部窗口目标 = CENTER
        round.setSequence(com.cloudmart.pet.util.PetJsonUtils.toJson(
                java.util.Collections.nCopies(10, "CENTER")));
        round.setOps("[]");
        round.setSuccessCount(0);
        round.setRewardEligible(true);
        return round;
    }

    private Map<String, Object> op(int window, String slot) {
        Map<String, Object> op = new HashMap<>();
        op.put("seq", window);
        op.put("windowIndex", window);
        op.put("slot", slot);
        return op;
    }

    @Test
    @DisplayName("R11：目标匹配+窗口内 → 操作事实落库，计数以事实表为权威")
    void submitAcceptsMatchingWindow() {
        PetMinigameRound round = activeRound();
        when(minigameMapper.selectById(77L)).thenReturn(round);
        when(operationMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> result = minigameService.submitOps(100L, 77L, List.of(op(1, "CENTER")));

        assertThat(result.get("accepted")).isEqualTo(1);
        assertThat(result.get("totalAccepted")).isEqualTo(3);
        ArgumentCaptor<PetMinigameOperation> captor = ArgumentCaptor.forClass(PetMinigameOperation.class);
        org.mockito.Mockito.verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getWindowIndex()).isEqualTo(1);
        assertThat(captor.getValue().getSlot()).isEqualTo("CENTER");
        // 投影更新限定列且带 ACTIVE 状态守卫（不触碰终态）
        org.mockito.Mockito.verify(minigameMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    @DisplayName("R11：目标不匹配 → 不落事实、不计分")
    void submitRejectsWrongSlot() {
        PetMinigameRound round = activeRound();
        when(minigameMapper.selectById(77L)).thenReturn(round);

        minigameService.submitOps(100L, 77L, List.of(op(1, "LEFT")));

        org.mockito.Mockito.verify(operationMapper, org.mockito.Mockito.never())
                .insert(any(PetMinigameOperation.class));
    }

    @Test
    @DisplayName("R11：并发同窗 DuplicateKey → 幂等跳过不抛出（每窗至多一次）")
    void submitDuplicateWindowIdempotent() {
        PetMinigameRound round = activeRound();
        when(minigameMapper.selectById(77L)).thenReturn(round);
        when(operationMapper.insert(any(PetMinigameOperation.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_minigame_operation_window"));

        Map<String, Object> result = minigameService.submitOps(100L, 77L, List.of(op(1, "CENTER")));

        assertThat(result.get("accepted")).isEqualTo(0);
    }

    @Test
    @DisplayName("R11：批次越界拒绝（1~10 条）与窗口越界拒绝")
    void submitValidatesBatchBounds() {
        PetMinigameRound round = activeRound();
        when(minigameMapper.selectById(77L)).thenReturn(round);

        assertThatThrownBy(() -> minigameService.submitOps(100L, 77L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR);
        assertThatThrownBy(() -> minigameService.submitOps(100L, 77L, List.of(op(11, "CENTER"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR);
        assertThatThrownBy(() -> minigameService.submitOps(100L, 77L, List.of(op(0, "CENTER"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR);
    }
}

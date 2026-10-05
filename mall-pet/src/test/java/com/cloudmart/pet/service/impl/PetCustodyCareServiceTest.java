package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R12 托管互斥与照顾事务：托管开始先加锁再互斥复验（跨表排他）；
 * 照顾在公开事务应用服务中执行（宠物恢复与计数原子）；
 * 到期/主动结束先结算最后一段照顾再转终态（T18）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("托管互斥与照顾测试（R12）")
class PetCustodyCareServiceTest {

    @Mock
    private PetMapper petMapper;
    @Mock
    private PetCustodyRecordMapper custodyMapper;

    private PetCustodyCareService careService;
    private PetCustodyCareTxWorker txWorker;
    private PetActivityMutex mutex;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
        TableInfoHelper.initTableInfo(assistant, PetCustodyRecord.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties properties = new PetProperties();
        PetClock clock = new PetClock(java.time.Clock.systemUTC(), properties);
        txWorker = new PetCustodyCareTxWorker(petMapper, custodyMapper, clock);
        careService = new PetCustodyCareService(petMapper, custodyMapper, clock,
                new com.cloudmart.pet.config.PetProperties(),
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetUserGuardService.class),
                org.mockito.Mockito.mock(PetActivityMutex.class), txWorker);
        mutex = new PetActivityMutex(
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetActivityMapper.class),
                custodyMapper, txWorker);
        lenient().when(custodyMapper.update(any(), any())).thenReturn(1);
        lenient().when(custodyMapper.updateById(any(PetCustodyRecord.class))).thenReturn(1);
        lenient().when(petMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("R12：ACTIVE 托管存在 → 活动开始入口互斥拒绝（跨表排他）")
    void custodyBlocksActivityStart() {
        when(custodyMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> mutex.requireFree(100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_USER_BUSY);
    }

    @Test
    @DisplayName("R12：无活动无托管 → 互斥放行")
    void freeWhenNothingBusy() {
        com.cloudmart.pet.repository.PetActivityMapper activityMapper =
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetActivityMapper.class);
        when(activityMapper.selectCount(any())).thenReturn(0L);
        when(custodyMapper.selectCount(any())).thenReturn(0L);
        PetActivityMutex realMutex = new PetActivityMutex(activityMapper, custodyMapper, txWorker);

        assertThatCode(() -> realMutex.requireFree(100L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R12 照顾：饥饿/清洁低于阈值恢复到 50，属性与计数同事务（一次调用内）")
    void applyCareRestoresThresholds() {
        Pet pet = pet(20, 25);
        when(petMapper.selectById(1L)).thenReturn(pet);
        PetCustodyRecord record = record(0, 0);

        careService.applyCare(record);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Pet>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(petMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        assertThat(record.getCareFeedUsed()).isEqualTo(1);
        assertThat(record.getCareCleanUsed()).isEqualTo(1);
        verify(custodyMapper).updateById(record);
    }

    @Test
    @DisplayName("R12 照顾：次数用尽不再恢复（有限照顾，不无限挂机）")
    void applyCareRespectsLimits() {
        Pet pet = pet(20, 25);
        when(petMapper.selectById(1L)).thenReturn(pet);
        PetCustodyRecord record = record(2, 1);

        careService.applyCare(record);

        verify(petMapper, never()).update(any(), any());
        verify(custodyMapper, never()).updateById(any(PetCustodyRecord.class));
    }

    @Test
    @DisplayName("R12 到期结束：先结算照顾再 CAS 转终态（最后一段不丢失）")
    void settleAndEndAppliesFinalCare() {
        Pet pet = pet(20, 25);
        when(petMapper.selectById(1L)).thenReturn(pet);
        PetCustodyRecord record = record(0, 0);

        careService.settleAndEnd(record);

        // 照顾先应用
        verify(petMapper).update(any(), any());
        verify(custodyMapper).updateById(record);
        // 再 CAS 结束
        verify(custodyMapper).update(any(), any());
    }

    @Test
    @DisplayName("PET-08/T12：到期记录结算仍应用最后一段照顾（原实现 endsAt 已过直接返回跳过）")
    void settleAndEndAppliesFinalCareForExpiredRecord() {
        Pet pet = pet(20, 25);
        when(petMapper.selectById(1L)).thenReturn(pet);
        PetCustodyRecord record = record(0, 0);
        record.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));

        careService.settleAndEnd(record);

        // 最终照顾先应用
        verify(petMapper).update(any(), any());
        verify(custodyMapper).updateById(record);
        // 再 CAS 结束
        verify(custodyMapper).update(any(), any());
    }

    @Test
    @DisplayName("PET-08/T12：到期托管在互斥检查前被惰性收尾，直接打工可成功")
    void mutexSettlesExpiredCustodyBeforeCheck() {
        Pet expiredPet = pet(20, 25);
        when(petMapper.selectById(1L)).thenReturn(expiredPet);
        PetCustodyRecord expired = record(0, 0);
        expired.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        com.cloudmart.pet.repository.PetActivityMapper activityMapper =
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetActivityMapper.class);
        when(activityMapper.selectCount(any())).thenReturn(0L);
        when(custodyMapper.selectOne(any())).thenReturn(expired);
        when(custodyMapper.selectCount(any())).thenReturn(0L);
        PetActivityMutex realMutex = new PetActivityMutex(activityMapper, custodyMapper, txWorker);

        assertThatCode(() -> realMutex.requireFree(100L)).doesNotThrowAnyException();
        // 到期托管已被收尾（照顾 + CAS ENDED）
        verify(custodyMapper).update(any(), any());
    }

    @Test
    @DisplayName("PET-08：未到期 ACTIVE 托管互斥仍拒绝（惰性收尾不误放行进行中托管）")
    void mutexStillRejectsActiveCustody() {
        PetCustodyRecord active = record(0, 0);
        when(custodyMapper.selectOne(any())).thenReturn(active);
        when(custodyMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> mutex.requireFree(100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_USER_BUSY);
        verify(custodyMapper, never()).update(any(), any());
    }

    private Pet pet(int hunger, int cleanliness) {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setHunger(hunger);
        pet.setCleanliness(cleanliness);
        pet.setLastStateUpdateAt(LocalDateTime.now(ZoneOffset.UTC));
        pet.setVersion(0);
        return pet;
    }

    private PetCustodyRecord record(int feedUsed, int cleanUsed) {
        PetCustodyRecord record = new PetCustodyRecord();
        record.setId(9L);
        record.setUserId(100L);
        record.setPetId(1L);
        record.setStatus("ACTIVE");
        record.setStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
        record.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(22));
        record.setCareFeedUsed(feedUsed);
        record.setCareCleanUsed(cleanUsed);
        return record;
    }
}

package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonPass;
import com.cloudmart.pet.entity.PetSeasonPassExpFact;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonPassExpFactMapper;
import com.cloudmart.pet.repository.PetSeasonPassMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T17：经验原子累计器——「插事实 + 初始化/锁定通行证 + 累加」同一事务，异常上抛
 * （REQUIRES_NEW 回滚事实与累计），不再出现「事实在、经验丢」的中间态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetSeasonPassExpAccumulator 单元测试（T17）")
class PetSeasonPassExpAccumulatorTest {

    private static final Long USER_ID = 8L;
    private static final Long SEASON_ID = 5L;

    @Mock
    private PetSeasonMapper seasonMapper;
    @Mock
    private PetSeasonPassMapper passMapper;
    @Mock
    private PetSeasonPassExpFactMapper expFactMapper;

    private PetSeasonPassExpAccumulator accumulator;

    @BeforeAll
    static void initEntityMeta() {
        for (Class<?> clazz : new Class<?>[]{PetSeason.class, PetSeasonPass.class, PetSeasonPassExpFact.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                TableInfoHelper.initTableInfo(
                        new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        com.cloudmart.pet.config.PetProperties properties = new com.cloudmart.pet.config.PetProperties();
        accumulator = new PetSeasonPassExpAccumulator(seasonMapper, passMapper, expFactMapper,
                new com.cloudmart.pet.config.PetClock(java.time.Clock.systemUTC(), properties));
    }

    private PetSeason activeSeason() {
        PetSeason season = new PetSeason();
        season.setId(SEASON_ID);
        season.setName("S1");
        season.setStatus("ACTIVE");
        season.setStartsAt(LocalDateTime.now().minusDays(1));
        season.setEndsAt(LocalDateTime.now().plusDays(10));
        return season;
    }

    private PetSeasonPass pass(int exp) {
        PetSeasonPass pass = new PetSeasonPass();
        pass.setId(1L);
        pass.setSeasonId(SEASON_ID);
        pass.setUserId(USER_ID);
        pass.setPassExp(exp);
        pass.setClaimedTiers("[]");
        pass.setVersion(0);
        return pass;
    }

    @Test
    @DisplayName("插事实并累加通行证经验（同事务）")
    void accumulate_insertsFactAndAccumulates() {
        when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
        when(passMapper.selectOne(any())).thenReturn(pass(10));
        when(expFactMapper.insert(any(PetSeasonPassExpFact.class))).thenReturn(1);

        boolean result = accumulator.accumulate(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-1");

        assertThat(result).isTrue();
        verify(expFactMapper).insert(any(PetSeasonPassExpFact.class));
        verify(passMapper).update(any(), any());
    }

    @Test
    @DisplayName("重复事实命中 uk 去重 → 不重复累加")
    void accumulate_duplicateFact_skipsAccumulation() {
        when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
        when(expFactMapper.insert(any(PetSeasonPassExpFact.class)))
                .thenThrow(new DuplicateKeyException("uk_fact"));

        boolean result = accumulator.accumulate(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-1");

        assertThat(result).isTrue();
        verify(passMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("无进行中赛季 → 返回 false（不记事实不累积）")
    void accumulate_noActiveSeason_returnsFalse() {
        when(seasonMapper.selectOne(any())).thenReturn(null);

        assertThat(accumulator.accumulate(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-1")).isFalse();
        verify(expFactMapper, never()).insert(any(PetSeasonPassExpFact.class));
    }

    @Test
    @DisplayName("累加失败异常上抛（不回滚吞掉）——REQUIRES_NEW 整体回滚事实与累计，重放可重做")
    void accumulate_whenAccumulationFails_propagatesNotSwallow() {
        when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
        when(passMapper.selectOne(any())).thenReturn(pass(10));
        when(expFactMapper.insert(any(PetSeasonPassExpFact.class))).thenReturn(1);
        when(passMapper.update(any(), any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> accumulator.accumulate(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-1"))
                .isInstanceOf(RuntimeException.class);
        // 关键：异常上抛，事务回滚 → 事实插入与累加都不落库；重放重新插事实并累加
    }

    @Test
    @DisplayName("按事实重算累计（校验任务数据源）")
    void expSumFromFacts_sumsAllFacts() {
        PetSeasonPassExpFact f1 = new PetSeasonPassExpFact();
        f1.setExp(5);
        PetSeasonPassExpFact f2 = new PetSeasonPassExpFact();
        f2.setExp(7);
        when(expFactMapper.selectList(any())).thenReturn(List.of(f1, f2));

        assertThat(accumulator.expSumFromFacts(SEASON_ID, USER_ID)).isEqualTo(12);
    }
}

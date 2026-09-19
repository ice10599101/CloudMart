package com.cloudmart.pet.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 战斗引擎纯函数测试：种子可复现、公式边界、回合上限（原文档 §13 契约）。
 */
@DisplayName("PetBattleEngine 单元测试")
class PetBattleEngineTest {

    private static final int MAX_ROUNDS = 15;

    private PetBattleEngine.Fighter fighter(long id, String name, int strength, int intelligence,
                                            int agility, int charm) {
        return new PetBattleEngine.Fighter(id, name, 100, 100, strength, intelligence, agility, charm);
    }

    @Test
    @DisplayName("同 seed 同快照：结果与回合流水完全一致（可复现）")
    void sameSeedReproducesExactly() {
        PetBattleEngine.Fighter atk = fighter(1L, "小橘", 30, 20, 25, 15);
        PetBattleEngine.Fighter def = fighter(2L, "旺财", 28, 22, 20, 18);

        PetBattleEngine.BattleResult r1 = PetBattleEngine.simulate(atk, def, 42L, MAX_ROUNDS);
        PetBattleEngine.BattleResult r2 = PetBattleEngine.simulate(atk, def, 42L, MAX_ROUNDS);

        assertThat(r1.winnerPetId()).isEqualTo(r2.winnerPetId());
        assertThat(r1.rounds()).hasSameSizeAs(r2.rounds());
        for (int i = 0; i < r1.rounds().size(); i++) {
            assertThat(r1.rounds().get(i)).isEqualTo(r2.rounds().get(i));
        }
    }

    @Test
    @DisplayName("不同 seed：统计上可能产生不同结果（1000 局双方都有胜场）")
    void differentSeedsCanProduceDifferentWinners() {
        PetBattleEngine.Fighter atk = fighter(1L, "小橘", 25, 20, 20, 15);
        PetBattleEngine.Fighter def = fighter(2L, "旺财", 25, 20, 20, 15);
        Set<Long> winners = new HashSet<>();
        for (long seed = 0; seed < 1000; seed++) {
            winners.add(PetBattleEngine.simulate(atk, def, seed, MAX_ROUNDS).winnerPetId());
        }
        assertThat(winners).contains(1L, 2L);
    }

    @Test
    @DisplayName("回合流水：伤害非负、每回合至多两次行动、HP 递减到 0 判负")
    void roundInvariants() {
        PetBattleEngine.Fighter atk = fighter(1L, "小橘", 50, 40, 30, 20);
        PetBattleEngine.Fighter def = fighter(2L, "旺财", 10, 10, 5, 5);
        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(atk, def, 7L, MAX_ROUNDS);

        assertThat(result.winnerPetId()).isEqualTo(1L);
        List<PetBattleEngine.Round> rounds = result.rounds();
        assertThat(rounds).isNotEmpty();
        for (PetBattleEngine.Round round : rounds) {
            assertThat(round.damage()).isGreaterThanOrEqualTo(0);
            if (!round.dodged()) {
                assertThat(round.damage()).isGreaterThan(0);
            }
        }
        // 最终一条受击流水：胜者击倒时 HP 为 0
        PetBattleEngine.Round last = rounds.get(rounds.size() - 1);
        assertThat(last.targetRemainingHp()).isZero();
        assertThat(last.targetPetId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("回合耗尽：按剩余 HP 百分比判定（防守方更满血则防守方胜）")
    void exhaustedRoundsJudgeByHpPercent() {
        // 双方伤害都极低但闪避极高 → 15 回合打不死
        PetBattleEngine.Fighter atk = fighter(1L, "小橘", 1, 1, 90, 0);
        PetBattleEngine.Fighter def = fighter(2L, "旺财", 1, 1, 90, 0);
        // 90 敏捷 → 闪避 13.5%，伤害 = (0.6+0.3+1~5) ≈ 2~6/次，15 回合 30 次攻击足够击杀
        // 调整为完全打不死：HP 提高
        atk = new PetBattleEngine.Fighter(1L, "小橘", 10000, 10000, 1, 1, 95, 0);
        def = new PetBattleEngine.Fighter(2L, "旺财", 9000, 10000, 1, 1, 95, 0);

        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(atk, def, 99L, MAX_ROUNDS);

        assertThat(result.rounds().size()).isLessThanOrEqualTo(MAX_ROUNDS * 2);
        // 攻击方 HP 剩余比例更高（10000 起步 vs 9000 起步）→ 攻击方胜
        assertThat(result.winnerPetId()).isEqualTo(1L);
        assertThat(result.attackerWon()).isTrue();
    }

    @Test
    @DisplayName("强方对弱方：快速击杀，回合数少")
    void strongBeatsWeakQuickly() {
        PetBattleEngine.Fighter atk = fighter(1L, "小橘", 90, 80, 70, 60);
        PetBattleEngine.Fighter def = fighter(2L, "旺财", 5, 5, 5, 5);
        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(atk, def, 1L, MAX_ROUNDS);

        assertThat(result.winnerPetId()).isEqualTo(1L);
        assertThat(result.rounds().size()).isLessThanOrEqualTo(4);
    }
}

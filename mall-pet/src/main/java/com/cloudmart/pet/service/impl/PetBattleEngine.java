package com.cloudmart.pet.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 宠物对战引擎（纯函数，无 Spring 依赖——可独立单测、可离线复现）。
 *
 * <p>规则（原文档 §13，异步回合制）：</p>
 * <ul>
 *   <li>出手顺序：敏捷 + 随机抖动，高者先手</li>
 *   <li>伤害 = 力量×0.6 + 智力×0.3 + rand(1~5)，暴击 ×1.5</li>
 *   <li>暴击率 = 5% + 魅力×0.2%（封顶 35%）；闪避率 = 敏捷×0.15%（封顶 25%）</li>
 *   <li>最多 maxRounds 回合，HP 归零判负；回合耗尽按剩余 HP 百分比判胜（无绝对平局）</li>
 * </ul>
 *
 * <p>seed 由服务端在挑战创建时生成并落库——同 seed 同快照重放结果一致，
 * 客户端（Cocos）只播放 {@link Round} 流水，无权计算结果。
 * 参战者以 petId 标识（名称仅展示，不参与判定，避免重名歧义）。</p>
 */
public final class PetBattleEngine {

    private PetBattleEngine() {
    }

    /** 参战者（对战快照字段，与 pet 表属性一一对应） */
    public record Fighter(Long petId, String name, int hp, int maxHp,
                          int strength, int intelligence, int agility, int charm) {
    }

    /** 回合流水（客户端播放单元）；targetPetId 标识受击方 */
    public record Round(int round, Long actorPetId, String actorName, String action,
                        int damage, boolean critical, boolean dodged,
                        Long targetPetId, int targetRemainingHp) {
    }

    /** 战斗结果 */
    public record BattleResult(List<Round> rounds, Long winnerPetId,
                               boolean attackerWon, boolean defenderWon) {
    }

    public static BattleResult simulate(Fighter attacker, Fighter defender, long seed, int maxRounds) {
        Random random = new Random(seed);
        List<Round> rounds = new ArrayList<>();
        // hp[0]=挑战方剩余 HP，hp[1]=防守方剩余 HP
        int[] hp = {attacker.hp(), defender.hp()};
        Long winner = null;

        for (int round = 1; round <= maxRounds && winner == null; round++) {
            // 先手：敏捷 + 随机抖动
            boolean attackerFirst = (attacker.agility() + random.nextInt(10))
                    >= (defender.agility() + random.nextInt(10));

            winner = strike(round, attacker, defender, attackerFirst, random, hp, rounds);
            if (winner != null) {
                break;
            }
            winner = strike(round, attacker, defender, !attackerFirst, random, hp, rounds);
        }

        if (winner == null) {
            // 回合耗尽：按剩余 HP 百分比判定（原文档 §1.8：平局按剩余 HP 判）
            double attackerPct = (double) hp[0] / Math.max(1, attacker.maxHp());
            double defenderPct = (double) hp[1] / Math.max(1, defender.maxHp());
            winner = attackerPct >= defenderPct ? attacker.petId() : defender.petId();
        }
        boolean attackerWon = winner.equals(attacker.petId());
        return new BattleResult(List.copyOf(rounds), winner, attackerWon, !attackerWon);
    }

    /**
     * 单次攻击：闪避 → 伤害/暴击 → 扣血。
     *
     * @return 受击方 petId（击倒时），否则 null
     */
    private static Long strike(int round, Fighter attacker, Fighter defender, boolean attackerActs,
                               Random random, int[] hp, List<Round> rounds) {
        Fighter actor = attackerActs ? attacker : defender;
        Fighter target = attackerActs ? defender : attacker;
        int targetIndex = attackerActs ? 1 : 0;

        // 闪避判定（封顶 25%）
        double dodgeRate = Math.min(0.25, target.agility() * 0.0015);
        if (random.nextDouble() < dodgeRate) {
            rounds.add(new Round(round, actor.petId(), actor.name(), "attack", 0, false, true,
                    target.petId(), hp[targetIndex]));
            return null;
        }
        // 伤害 = 力量×0.6 + 智力×0.3 + rand(1~5)，暴击 ×1.5
        double criticalRate = Math.min(0.35, 0.05 + actor.charm() * 0.002);
        boolean critical = random.nextDouble() < criticalRate;
        int damage = (int) Math.round(
                (actor.strength() * 0.6 + actor.intelligence() * 0.3 + random.nextInt(5) + 1)
                        * (critical ? 1.5 : 1));
        hp[targetIndex] = Math.max(0, hp[targetIndex] - damage);
        rounds.add(new Round(round, actor.petId(), actor.name(), "attack", damage, critical, false,
                target.petId(), hp[targetIndex]));

        // 击倒：胜者是出手方（actor），而非倒下的受击方
        return hp[targetIndex] <= 0 ? actor.petId() : null;
    }
}

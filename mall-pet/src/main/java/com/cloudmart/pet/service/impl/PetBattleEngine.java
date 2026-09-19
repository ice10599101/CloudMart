package com.cloudmart.pet.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 宠物对战引擎（纯函数，无 Spring 依赖——可独立单测、可离线复现）。
 *
 * <p>规则（原文档 §13，异步回合制）：</p>
 * <ul>
 *   <li>出手顺序：敏捷 + 技能先手加成 + 随机抖动，高者先手</li>
 *   <li>伤害 = 力量×0.6 + 智力×0.3 + 装备加成 + rand(1~5)，暴击 ×1.5，受击方按技能减免</li>
 *   <li>暴击率 = 5% + 魅力×0.2% + 技能加成（封顶 35%）；闪避率 = 敏捷×0.15% + 技能加成（封顶 25%）</li>
 *   <li>主动技 POWER_STRIKE：首回合伤害 ×(1+加成)，回合流水 action=skill（客户端据此播技能演出）</li>
 *   <li>最多 maxRounds 回合，HP 归零判负；回合耗尽按剩余 HP 百分比判胜（无绝对平局）</li>
 * </ul>
 *
 * <p>seed 由服务端在挑战创建时生成并落库——同 seed 同快照重放结果一致，
 * 客户端（Cocos）只播放 {@link Round} 流水，无权计算结果。
 * 参战者以 petId 标识（名称仅展示，不参与判定，避免重名歧义）。
 * 装备/技能加成在挑战时刻已并入快照（原文档 §1.8：中途养成不影响已发起挑战）。</p>
 */
public final class PetBattleEngine {

    /** 闪避率封顶 */
    private static final double MAX_DODGE_RATE = 0.25;
    /** 暴击率封顶 */
    private static final double MAX_CRIT_RATE = 0.35;
    /** 受伤减免封顶（与 PetStatsService 同口径，引擎侧兜底防止快照被篡改） */
    private static final double MAX_DAMAGE_REDUCTION = 0.5;

    private PetBattleEngine() {
    }

    /**
     * 参战者（对战快照字段，与 pet 表属性 + 装备/技能加成一一对应）。
     *
     * <p>8 参构造保留为"无加成"兼容入口（PvE 野生宠物、历史快照解析）。</p>
     */
    public record Fighter(Long petId, String name, int hp, int maxHp,
                          int strength, int intelligence, int agility, int charm,
                          double critBonus, double dodgeBonus, int damageBonus,
                          double powerStrikeBonus, double damageReduction, int firstStrikeBonus) {

        /** 无装备/技能加成的参战者 */
        public Fighter(Long petId, String name, int hp, int maxHp,
                       int strength, int intelligence, int agility, int charm) {
            this(petId, name, hp, maxHp, strength, intelligence, agility, charm, 0, 0, 0, 0, 0, 0);
        }
    }

    /** 回合流水（客户端播放单元）；targetPetId 标识受击方，action=skill 表示主动技生效 */
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
            // 先手：敏捷 + 技能先手加成 + 随机抖动
            boolean attackerFirst = (attacker.agility() + attacker.firstStrikeBonus() + random.nextInt(10))
                    >= (defender.agility() + defender.firstStrikeBonus() + random.nextInt(10));

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
     * 单次攻击：闪避 → 伤害/暴击/主动技/受伤减免 → 扣血。
     *
     * @return 受击方 petId（击倒时），否则 null
     */
    private static Long strike(int round, Fighter attacker, Fighter defender, boolean attackerActs,
                               Random random, int[] hp, List<Round> rounds) {
        Fighter actor = attackerActs ? attacker : defender;
        Fighter target = attackerActs ? defender : attacker;
        int targetIndex = attackerActs ? 1 : 0;

        // 闪避判定（封顶 25%）
        double dodgeRate = Math.min(MAX_DODGE_RATE, target.agility() * 0.0015 + target.dodgeBonus());
        if (random.nextDouble() < dodgeRate) {
            rounds.add(new Round(round, actor.petId(), actor.name(), "attack", 0, false, true,
                    target.petId(), hp[targetIndex]));
            return null;
        }
        // 伤害 = (力量×0.6 + 智力×0.3 + 装备加成 + rand(1~5)) × 暴击 × 主动技 × (1-受击减免)
        double criticalRate = Math.min(MAX_CRIT_RATE, 0.05 + actor.charm() * 0.002 + actor.critBonus());
        boolean critical = random.nextDouble() < criticalRate;
        boolean skillStrike = round == 1 && actor.powerStrikeBonus() > 0;
        double damage = actor.strength() * 0.6 + actor.intelligence() * 0.3
                + actor.damageBonus() + random.nextInt(5) + 1;
        if (critical) {
            damage *= 1.5;
        }
        if (skillStrike) {
            damage *= 1 + actor.powerStrikeBonus();
        }
        double reduction = Math.min(MAX_DAMAGE_REDUCTION, Math.max(0, target.damageReduction()));
        int finalDamage = (int) Math.round(damage * (1 - reduction));
        hp[targetIndex] = Math.max(0, hp[targetIndex] - finalDamage);
        rounds.add(new Round(round, actor.petId(), actor.name(), skillStrike ? "skill" : "attack",
                finalDamage, critical, false, target.petId(), hp[targetIndex]));

        // 击倒：胜者是出手方（actor），而非倒下的受击方
        return hp[targetIndex] <= 0 ? actor.petId() : null;
    }
}

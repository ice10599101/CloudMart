package com.cloudmart.pet.util;

import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;

import java.util.List;

/**
 * 亲密度等级计算（纯函数，无状态）。
 *
 * <p>抽成工具类的唯一原因：{@code PetStateService.grantExp} 需要把亲密度的经验加成
 * 并入经验发放，而 {@code PetStateService} ← 成就服务 ← 亲密度服务之间不能形成循环依赖，
 * 因此等级/加成公式放在两侧都能引用的无依赖工具里，避免公式出现两份实现。</p>
 */
public final class PetIntimacyMath {

    private PetIntimacyMath() {
    }

    /** 亲密度等级（1 起） */
    public static int levelOf(int intimacy, List<Integer> thresholds) {
        int level = 1;
        for (int i = 0; i < thresholds.size(); i++) {
            if (intimacy >= thresholds.get(i)) {
                level = i + 1;
            }
        }
        return level;
    }

    /** 等级名（越界回落 Lv.N） */
    public static String levelName(int level, List<String> names) {
        if (names == null || names.isEmpty()) {
            return "Lv." + level;
        }
        return names.get(Math.max(0, Math.min(names.size() - 1, level - 1)));
    }

    /** 距下一等级还需点数（满级 0） */
    public static int toNext(int intimacy, List<Integer> thresholds) {
        for (Integer threshold : thresholds) {
            if (intimacy < threshold) {
                return threshold - intimacy;
            }
        }
        return 0;
    }

    /** 下一等级阈值（满级 null） */
    public static Integer nextThreshold(int intimacy, List<Integer> thresholds) {
        for (Integer threshold : thresholds) {
            if (intimacy < threshold) {
                return threshold;
            }
        }
        return null;
    }

    /** 当前等级起点阈值 */
    public static int floorThreshold(int level, List<Integer> thresholds) {
        if (thresholds.isEmpty()) {
            return 0;
        }
        return thresholds.get(Math.min(Math.max(level - 1, 0), thresholds.size() - 1));
    }

    /** 亲密度带来的经验加成（0.01 = 1%，上限取自配置） */
    public static double expBonus(Pet pet, PetProperties.Intimacy cfg) {
        if (pet == null || pet.getIntimacy() == null || cfg == null) {
            return 0;
        }
        int level = levelOf(pet.getIntimacy(), cfg.getLevelThresholds());
        double bonus = Math.max(0, level - 1) * cfg.getExpBonusPerLevel();
        return Math.min(cfg.getMaxExpBonus(), bonus);
    }

    /** 经验加成的展示百分比（四舍五入取整） */
    public static int expBonusPercent(Pet pet, PetProperties.Intimacy cfg) {
        return (int) Math.round(expBonus(pet, cfg) * 100);
    }
}

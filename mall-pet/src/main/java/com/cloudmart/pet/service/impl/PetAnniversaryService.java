package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.controller.PetAnniversaryController.AnniversaryVO;
import com.cloudmart.pet.entity.Pet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 宠物纪念日计算（PET-05：可注入 PetClock——业务区 Asia/Shanghai 口径，测试可固定时钟）。
 *
 * <p>里程碑语义修正：</p>
 * <ul>
 *   <li>currentMilestone = <b>最近已达成</b>的里程碑（周年当天返回当前周年，
 *       而非恒返回百日）；未满百日返回 null；</li>
 *   <li>nextMilestone = <b>严格未来</b>项（daysToGo &gt; 0，当天达成的归 current），
 *       不再跳过周年当天；</li>
 *   <li>周年按领养日期 {@code plusYears(N)} 递推（闰年由 java.time 处理，
 *       2/29 领养顺延为 2/28）；统一业务日（pet.businessZone）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PetAnniversaryService {

    /** 百日纪念 */
    private static final long MILESTONE_100_DAYS = 100;
    /** 临近窗口（天）：里程碑在 7 天内到达时前端可展示庆祝钩子 */
    private static final int UPCOMING_WINDOW_DAYS = 7;
    /** 周年追溯上限（防御性：避免无限循环，20 周年足够） */
    private static final int MAX_ANNIVERSARY_YEARS = 20;

    private final PetClock petClock;

    /** 纪念日卡片（days/companionStreak/当前/下一里程碑） */
    public AnniversaryVO card(Pet pet) {
        LocalDate adoptedAt = adoptedDateOf(pet);
        LocalDate today = petClock.businessDate();
        long days = Math.max(0, ChronoUnit.DAYS.between(adoptedAt, today));
        return new AnniversaryVO(
                days,
                pet.getCompanionStreak() != null ? pet.getCompanionStreak() : 0,
                currentMilestone(adoptedAt, today, days),
                nextMilestone(adoptedAt, today, days));
    }

    /** 领养日期（业务区口径：created_at UTC → 业务区日期） */
    public LocalDate adoptedDateOf(Pet pet) {
        return pet.getCreatedAt() != null
                ? pet.getCreatedAt().atZone(java.time.ZoneOffset.UTC)
                        .withZoneSameInstant(petClock.businessZone()).toLocalDate()
                : petClock.businessDate();
    }

    /** 最近已达成的里程碑：周年优先（比百日更晚达成）；未满百日 null */
    AnniversaryVO.Milestone currentMilestone(LocalDate adoptedAt, LocalDate today, long days) {
        int latestAnniversary = 0;
        for (int years = 1; years <= MAX_ANNIVERSARY_YEARS; years++) {
            long target = ChronoUnit.DAYS.between(adoptedAt, adoptedAt.plusYears(years));
            if (days >= target) {
                latestAnniversary = years;
            } else {
                break;
            }
        }
        if (latestAnniversary >= 1) {
            return new AnniversaryVO.Milestone(
                    "ANNIVERSARY_" + latestAnniversary, latestAnniversary + " 周年纪念", 0);
        }
        if (days >= MILESTONE_100_DAYS) {
            return new AnniversaryVO.Milestone("100_DAYS", "百日纪念", 0);
        }
        return null;
    }

    /** 严格未来的下一个里程碑（daysToGo > 0）；当天达成的不进 next */
    AnniversaryVO.Milestone nextMilestone(LocalDate adoptedAt, LocalDate today, long days) {
        if (days < MILESTONE_100_DAYS) {
            return upcoming("100_DAYS", "百日纪念", MILESTONE_100_DAYS - days);
        }
        for (int years = 1; years <= MAX_ANNIVERSARY_YEARS; years++) {
            long target = ChronoUnit.DAYS.between(adoptedAt, adoptedAt.plusYears(years));
            if (days < target) {
                return upcoming("ANNIVERSARY_" + years, years + " 周年纪念", target - days);
            }
        }
        return null;
    }

    /** 临近（≤7 天）打 imminent 标记由 title 承载（契约三字段，前端按 daysToGo 判断） */
    private AnniversaryVO.Milestone upcoming(String key, String title, long daysToGo) {
        if (daysToGo <= UPCOMING_WINDOW_DAYS) {
            return new AnniversaryVO.Milestone(key, title + "（" + daysToGo + " 天后）", daysToGo);
        }
        return new AnniversaryVO.Milestone(key, title, daysToGo);
    }
}

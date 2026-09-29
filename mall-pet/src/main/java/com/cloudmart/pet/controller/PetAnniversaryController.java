package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.service.PetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 宠物纪念日（F7，情感化低成本项）：领养天数、陪伴连续天数、临近里程碑。
 *
 * <p>纯计算端点（无新表）：领养日取 pet.created_at，连续陪伴取 companion_streak；
 * 里程碑口径——领养满 100 天「百日纪念」、满 N 周年（365 天整），临近=未来 7 天内到达。
 * 纪念日提醒（周年/百日当日推送）由 PetReminderService.evaluateOnVisit 惰性触发。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物纪念日", description = "领养天数/陪伴里程碑/临近纪念日（F7）")
@RequiredArgsConstructor
public class PetAnniversaryController {

    /** 临近窗口（天）：里程碑在 7 天内到达时前端可展示庆祝钩子 */
    private static final int UPCOMING_WINDOW_DAYS = 7;
    /** 百日纪念 */
    private static final long MILESTONE_100_DAYS = 100;

    private final PetService petService;

    /** 纪念日卡片视图 */
    public record AnniversaryVO(
            long adoptionDays,
            int companionStreak,
            Milestone currentMilestone,
            Milestone nextMilestone) {

        /** 里程碑：key 稳定机器可读（100_DAYS/ANNIVERSARY_1...），daysToGo 负数表示已过 */
        public record Milestone(String key, String title, long daysToGo) {
        }
    }

    @GetMapping("/me/anniversaries")
    @Operation(summary = "我的宠物纪念日", description = "领养 X 天 / 连续陪伴 X 天 / 当前与下一个里程碑（百日、周年）；无宠物抛 PET_NOT_FOUND")
    public ApiResponse<AnniversaryVO> anniversaries(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        LocalDate adoptedAt = pet.getCreatedAt() != null
                ? pet.getCreatedAt().atZone(ZoneId.of("UTC")).toLocalDate()
                : LocalDate.now(ZoneId.of("UTC"));
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        long days = ChronoUnit.DAYS.between(adoptedAt, today);

        return ApiResponse.ok(new AnniversaryVO(
                days,
                pet.getCompanionStreak() != null ? pet.getCompanionStreak() : 0,
                currentMilestone(days),
                nextMilestone(adoptedAt, today, days)));
    }

    /** 当前所处里程碑（已达成且最近的一个；未满百日返回 null） */
    private AnniversaryVO.Milestone currentMilestone(long days) {
        if (days >= MILESTONE_100_DAYS) {
            return new AnniversaryVO.Milestone("100_DAYS", "百日纪念", 0);
        }
        return null;
    }

    /** 下一个里程碑：百日 → 1~N 周年；已过里程碑 daysToGo 为 0，未达成返回距今天数 */
    private AnniversaryVO.Milestone nextMilestone(LocalDate adoptedAt, LocalDate today, long days) {
        if (days < MILESTONE_100_DAYS) {
            return new AnniversaryVO.Milestone("100_DAYS", "百日纪念",
                    MILESTONE_100_DAYS - days);
        }
        long firstAnniversary = ChronoUnit.DAYS.between(adoptedAt, adoptedAt.plusYears(1));
        if (days < firstAnniversary) {
            return upcoming("ANNIVERSARY_1", "1 周年纪念", firstAnniversary - days);
        }
        // 周年序列：按年数递推找下一个未达周年
        for (int years = 2; years <= 10; years++) {
            long target = ChronoUnit.DAYS.between(adoptedAt, adoptedAt.plusYears(years));
            if (days < target) {
                return upcoming("ANNIVERSARY_" + years, years + " 周年纪念", target - days);
            }
        }
        return new AnniversaryVO.Milestone("ANNIVERSARY_10", "10 周年纪念", 0);
    }

    /** 临近（≤7 天）打 imminent 标记由 title 承载（数据契约保持三个字段，前端按 daysToGo 判断） */
    private AnniversaryVO.Milestone upcoming(String key, String title, long daysToGo) {
        if (daysToGo <= UPCOMING_WINDOW_DAYS) {
            return new AnniversaryVO.Milestone(key, title + "（" + daysToGo + " 天后）", daysToGo);
        }
        return new AnniversaryVO.Milestone(key, title, daysToGo);
    }

    /** 供提醒服务复用的里程碑判定（同口径）：满百日或整周年当天 */
    public static boolean isMilestoneDay(long adoptionDays) {
        return adoptionDays == MILESTONE_100_DAYS || adoptionDays % 365 == 0;
    }
}

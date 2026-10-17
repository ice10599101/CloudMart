package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.controller.PetAnniversaryController.AnniversaryVO;
import com.cloudmart.pet.entity.Pet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PET-05 验收：99/100/101 天、周年前/当天/次日、2 月 29 日领养（闰年）、时钟注入。
 */
class PetAnniversaryServiceTest {

    private static final ZoneId BIZ = ZoneId.of("Asia/Shanghai");

    private PetAnniversaryService serviceOf(LocalDate bizToday) {
        // 固定时钟：把"业务区当天"钉死（Asia/Shanghai 正午 = UTC 04:00，同日无跨日歧义）
        Instant fixed = bizToday.atTime(12, 0).atZone(BIZ).toInstant();
        return new PetAnniversaryService(new PetClock(Clock.fixed(fixed, ZoneId.of("UTC")), new PetProperties()));
    }

    private Pet petAdoptedAt(LocalDateTime createdAtUtc) {
        Pet pet = new Pet();
        pet.setCreatedAt(createdAtUtc);
        pet.setCompanionStreak(5);
        return pet;
    }

    @Test
    @DisplayName("99 天：current=null，next=百日（1 天后，进临近窗口）")
    void day99() {
        PetAnniversaryService service = serviceOf(LocalDate.of(2026, 5, 10));
        // 领养 = today - 99 天
        Pet pet = petAdoptedAt(LocalDate.of(2026, 1, 31).atTime(4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.adoptionDays()).isEqualTo(99);
        assertThat(vo.currentMilestone()).isNull();
        assertThat(vo.nextMilestone().key()).isEqualTo("100_DAYS");
        assertThat(vo.nextMilestone().daysToGo()).isEqualTo(1);
    }

    @Test
    @DisplayName("100 天：current=百日（当天达成归 current），next=1 周年")
    void day100() {
        PetAnniversaryService service = serviceOf(LocalDate.of(2026, 5, 10));
        Pet pet = petAdoptedAt(LocalDate.of(2026, 1, 30).atTime(4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.adoptionDays()).isEqualTo(100);
        assertThat(vo.currentMilestone().key()).isEqualTo("100_DAYS");
        assertThat(vo.currentMilestone().daysToGo()).isEqualTo(0);
        assertThat(vo.nextMilestone().key()).isEqualTo("ANNIVERSARY_1");
    }

    @Test
    @DisplayName("101 天：current=百日（不再恒跳百日之外的新语义破坏）")
    void day101() {
        PetAnniversaryService service = serviceOf(LocalDate.of(2026, 5, 10));
        Pet pet = petAdoptedAt(LocalDate.of(2026, 1, 29).atTime(4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.currentMilestone().key()).isEqualTo("100_DAYS");
        assertThat(vo.nextMilestone().key()).isEqualTo("ANNIVERSARY_1");
    }

    @Test
    @DisplayName("周年当天：current=1 周年（原缺陷恒返回百日/next 跳到 2 周年——已修正）")
    void firstAnniversaryDay() {
        PetAnniversaryService service = serviceOf(LocalDate.of(2026, 5, 10));
        // 领养日 = 2025-05-10（次年 5-10 为 1 周年当天）
        Pet pet = petAdoptedAt(LocalDate.of(2025, 5, 10).atTime(4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.currentMilestone().key()).isEqualTo("ANNIVERSARY_1");
        assertThat(vo.currentMilestone().daysToGo()).isEqualTo(0);
        assertThat(vo.nextMilestone().key()).isEqualTo("ANNIVERSARY_2");
    }

    @Test
    @DisplayName("周年次日：current 仍为 1 周年（最近已达成），next=2 周年")
    void dayAfterAnniversary() {
        PetAnniversaryService service = serviceOf(LocalDate.of(2026, 5, 11));
        Pet pet = petAdoptedAt(LocalDate.of(2025, 5, 10).atTime(4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.currentMilestone().key()).isEqualTo("ANNIVERSARY_1");
        assertThat(vo.nextMilestone().key()).isEqualTo("ANNIVERSARY_2");
    }

    @Test
    @DisplayName("2 月 29 日领养（闰年）：周年按 plusYears 顺延为 2/28，当天判定正确")
    void leapYearAdoption() {
        // 领养 2024-02-29（闰年日），2025 年周年日顺延为 2025-02-28
        PetAnniversaryService service = serviceOf(LocalDate.of(2025, 2, 28));
        Pet pet = petAdoptedAt(LocalDateTime.of(2024, 2, 29, 4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.currentMilestone().key()).isEqualTo("ANNIVERSARY_1");
        assertThat(vo.currentMilestone().daysToGo()).isEqualTo(0);
    }

    @Test
    @DisplayName("十年老宠：current=10 周年（原实现 current 恒百日——已修正）")
    void tenYearOldPet() {
        PetAnniversaryService service = serviceOf(LocalDate.of(2026, 5, 10));
        Pet pet = petAdoptedAt(LocalDate.of(2016, 5, 10).atTime(4, 0));
        AnniversaryVO vo = service.card(pet);
        assertThat(vo.currentMilestone().key()).isEqualTo("ANNIVERSARY_10");
    }
}

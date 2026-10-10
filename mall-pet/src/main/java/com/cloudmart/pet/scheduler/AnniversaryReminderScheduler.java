package com.cloudmart.pet.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.mq.PetEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 纪念日推送（§6 纪念日推送）：每日 09:00 UTC 扫描领养满 100 天整/整数周年的宠物，
 * 经 outbox 发布 ANNIVERSARY 事件 → mall-notification 落站内通知
 * （N-1 订阅消息通道在通知服务侧挂接）。eventId=pet:{petId}:{days} 幂等。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnniversaryReminderScheduler {

    /** 里程碑天数：百日 + 1..3 周年（与 PetAnniversaryController 里程碑口径一致） */
    private static final List<Long> MILESTONE_DAYS = List.of(100L, 365L, 730L, 1095L);

    private final PetMapper petMapper;
    private final PetEventProducer eventProducer;

    @Scheduled(cron = "0 0 9 * * ?", zone = "UTC")
    public void scanAnniversaries() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        // 一次性拉取今天达到任一里程碑天数的宠物（DATEDIFF 口径，索引不友好但每日一次、量级可控）
        List<Pet> hits = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .select(Pet::getId, Pet::getUserId, Pet::getName)
                .and(w -> {
                    for (Long days : MILESTONE_DAYS) {
                        LocalDate target = today.minusDays(days);
                        w.or(inner -> inner
                                .apply("MONTH(created_at) = {0}", target.getMonthValue())
                                .apply("DAY(created_at) = {0}", target.getDayOfMonth())
                                .apply("YEAR(created_at) = {0}", target.getYear()));
                    }
                }));
        if (hits.isEmpty()) {
            log.info("纪念日扫描：今日无里程碑宠物");
            return;
        }
        int sent = 0;
        for (Pet pet : hits) {
            long days = pet.getCreatedAt() == null ? 0
                    : ChronoUnit.DAYS.between(pet.getCreatedAt().atZone(ZoneId.of("UTC")).toLocalDate(), today);
            String milestoneName = days == 100 ? "百日" : (days / 365) + " 周年";
            String eventId = "pet:" + pet.getId() + ":" + days;
            try {
                eventProducer.publishViaOutbox(com.cloudmart.pet.config.RocketMQConfig.PET_TAG_PROACTIVE,
                        new PetEventProducer.PetEventMessage(
                                eventId,
                                String.valueOf(pet.getUserId()),
                                "ANNIVERSARY",
                                "🎉 " + pet.getName() + " 的" + milestoneName + "纪念",
                                pet.getName() + " 已经陪伴你 " + days + " 天啦！今天是个值得纪念的日子，去看看它吧～",
                                String.valueOf(pet.getId()),
                                "PET"));
                sent++;
            } catch (Exception e) {
                log.warn("纪念日事件发布失败（跳过）: petId={}, err={}", pet.getId(), e.getMessage());
            }
        }
        log.info("纪念日扫描完成: 里程碑宠物 {} 只，事件已发布 {}", hits.size(), sent);
    }
}

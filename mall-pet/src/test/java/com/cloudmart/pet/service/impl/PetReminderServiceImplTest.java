package com.cloudmart.pet.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.feign.NotificationFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.service.PetReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物提醒测试：未读数降级策略、配额耗尽时不再空耗触发点当日幂等标记。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetReminderServiceImpl 单元测试")
class PetReminderServiceImplTest {

    @Mock
    private NotificationFeignClient notificationFeignClient;
    @Mock
    private com.cloudmart.pet.feign.CommunityActivityFeignClient activityFeignClient;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetContextService contextService;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private com.cloudmart.pet.service.PetEventService eventService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final PetProperties properties = new PetProperties();
    private PetReminderServiceImpl reminderService;

    @BeforeEach
    void setUp() {
        reminderService = new PetReminderServiceImpl(notificationFeignClient,
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetNotifyPrefMapper.class),
                activityFeignClient,
                activityMapper, contextService, eventProducer, properties, redisTemplate, eventService);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 社区宠物活动达成提醒：默认无活动（不额外触发提醒，不影响既有断言）
        lenient().when(eventService.eventsForPet(any())).thenReturn(java.util.List.of());
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setHunger(80);
        return pet;
    }

    @Test
    @DisplayName("未读数：正常返回 Feign 结果")
    void unreadCountReturnsFeignValue() {
        when(notificationFeignClient.getUnreadCount(100L, "PET")).thenReturn(ApiResponse.ok(3L));

        assertThat(reminderService.unreadCount(100L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("未读数：Feign 降级 Fail-Open 返回 0，不抛异常")
    void unreadCountFailsOpen() {
        when(notificationFeignClient.getUnreadCount(100L, "PET"))
                .thenThrow(new IllegalStateException("notification down"));

        assertThat(reminderService.unreadCount(100L)).isZero();
    }

    @Test
    @DisplayName("配额耗尽：不消耗触发点当日幂等标记（修复后当天可补发）")
    void quotaExhaustedDoesNotConsumeTriggerFlag() {
        // 间隔频控放行
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(true);
        // 当日配额已用满（dailyLimit 默认 3）
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(properties.getProactive().getDailyLimit()));

        reminderService.evaluateOnVisit(100L, pet());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(keys.capture(), anyString(), any(java.time.Duration.class));
        assertThat(keys.getValue()).doesNotContain("pet:proactive:sent");
        verify(eventProducer, never()).publish(anyString(), any(PetEventProducer.PetEventMessage.class));
    }

    @Test
    @DisplayName("提醒列表：映射优先级与已读态")
    void listRemindersMapsPriorityAndReadState() {
        when(notificationFeignClient.listNotifications(100L, "PET", 1, 20))
                .thenReturn(ApiResponse.ok(java.util.List.of(
                        new NotificationFeignClient.NotificationItemVO(11L, "PET", "主人，你有一条新的消息",
                                "有 2 条私信还没看哦", null, "PET_MESSAGE", false, "2026-01-01T00:00:00"),
                        new NotificationFeignClient.NotificationItemVO(12L, "PET", "每日问候", "早安主人～",
                                null, "PET_DAILY_GREETING", true, "2026-01-01T00:00:00"))));

        java.util.List<com.cloudmart.pet.vo.PetReminderVO> reminders = reminderService.listReminders(100L);

        assertThat(reminders).hasSize(2);
        assertThat(reminders.get(0).priority()).isEqualTo("P0");
        assertThat(reminders.get(0).isRead()).isFalse();
        assertThat(reminders.get(1).priority()).isEqualTo("P2");
        assertThat(reminders.get(1).isRead()).isTrue();
    }

    @Test
    @DisplayName("提醒服务实现 PetReminderService 接口契约")
    void implementsReminderServiceContract() {
        assertThat(reminderService).isInstanceOf(PetReminderService.class);
    }
}

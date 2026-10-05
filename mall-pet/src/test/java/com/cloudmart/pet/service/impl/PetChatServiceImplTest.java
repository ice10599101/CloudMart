package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.dto.PetChatRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetChatSession;
import com.cloudmart.pet.entity.PetChatMessage;
import com.cloudmart.pet.enums.PetChatRole;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.repository.PetChatMessageMapper;
import com.cloudmart.pet.repository.PetChatSessionMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetMemoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetChatMessageVO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
/**
 * 宠物聊天三层结构测试：危机词拦截 / 固定行为模板 / AI 降级模板（Fail-Open）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetChatServiceImpl 单元测试")
class PetChatServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetContextService contextService;
    @Mock
    private PetAiClient aiClient;
    @Mock
    private PetChatSessionMapper sessionMapper;
    @Mock
    private PetChatMessageMapper messageMapper;
    @Mock
    private PetMemoryMapper memoryMapper;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetDailyQuestService dailyQuestService;
    @Mock
    private PetIntimacyService intimacyService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PetContentSafetyService safetyService;
    @Mock
    private com.cloudmart.pet.repository.PetReportMapper reportMapper;
    @Mock
    private com.cloudmart.pet.config.PetMetrics metrics;
    @Mock
    private com.cloudmart.pet.repository.PetCareerConfigMapper careerConfigMapper;
    @Mock
    private PetPersonaPhraseService personaPhraseService;

    private PetChatServiceImpl chatService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
        TableInfoHelper.initTableInfo(assistant, PetChatSession.class);
        TableInfoHelper.initTableInfo(assistant, com.cloudmart.pet.entity.PetChatMessage.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties chatProps = new PetProperties();
        // B18：短事务模板——测试中直接执行回调（无真实事务资源）
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                org.mockito.Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class);
        org.mockito.Mockito.when(txTemplate.execute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> ((org.springframework.transaction.support.TransactionCallback<?>) inv.getArgument(0))
                        .doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class)));
        chatService = new PetChatServiceImpl(petService, contextService, aiClient, sessionMapper,
                messageMapper, memoryMapper, petMapper, achievementService, dailyQuestService,
                intimacyService, chatProps, redisTemplate, txTemplate, safetyService, reportMapper,
                metrics, careerConfigMapper, personaPhraseService);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(3);
        pet.setHunger(80);
        pet.setHappiness(90);
        pet.setEnergy(70);
        pet.setCleanliness(80);
        pet.setStatus("IDLE");
        pet.setPersonality("LIVELY");
        pet.setLastStateUpdateAt(LocalDateTime.now(ZoneId.of("UTC")));
        pet.setVersion(0);
        return pet;
    }

    private PetChatSession session() {
        PetChatSession session = new PetChatSession();
        session.setId(9L);
        session.setUserId(100L);
        session.setPetId(1L);
        return session;
    }

    @Test
    @DisplayName("危机词本地拦截：安抚 + 热线资源，不调用 AI（数据安全）")
    void crisisKeywordBlocksAi() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        when(safetyService.isCrisis("我不想活了")).thenReturn(true);
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);

        PetChatMessageVO vo = chatService.chat(100L, new PetChatRequest("我不想活了"));

        assertThat(vo.content()).contains("12356");
        assertThat(vo.isAiReply()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(aiClient);
        // P0-1：危机词命中必须自动生成举报记录（进入管理端处理队列）
        org.mockito.Mockito.verify(reportMapper).insert(any(com.cloudmart.pet.entity.PetReport.class));
    }

    @Test
    @DisplayName("第一层固定行为：问名字走模板，不调 AI")
    void fixedIntentUsesTemplate() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);

        PetChatMessageVO vo = chatService.chat(100L, new PetChatRequest("你叫什么名字呀？"));

        assertThat(vo.content()).contains("小橘");
        assertThat(vo.isAiReply()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(aiClient);
    }

    @Test
    @DisplayName("AI 不可用：降级模板回复（isAiReply=false），陪伴不中断")
    void aiFailureFallsBackToTemplate() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(java.util.List.of());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);
        when(aiClient.generateReply(anyString(), anyString()))
                .thenThrow(new com.cloudmart.common.exception.BusinessException(
                        "PET_AI_UNAVAILABLE", "AI 服务暂时不可用"));
        when(contextService.buildContext(any(), any())).thenReturn(new PetContextService.PetContext(
                "小橘", 3, "LIVELY", 80, 90, 70, 80, "空闲中", 2, 5, 1, 0, false, java.util.List.of()));

        PetChatMessageVO vo = chatService.chat(100L, new PetChatRequest("今天我们做点什么好呢？"));

        assertThat(vo.content()).contains("5 个赞");
        assertThat(vo.isAiReply()).isFalse();
    }

    @Test
    @DisplayName("AI 正常：返回 AI 回复（isAiReply=true）")
    void aiReplyPath() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(java.util.List.of());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);
        when(aiClient.generateReply(anyString(), anyString())).thenReturn("好呀好呀！我们一起去玩！");
        when(contextService.buildContext(any(), any())).thenReturn(new PetContextService.PetContext(
                "小橘", 3, "LIVELY", 80, 90, 70, 80, "空闲中", 0, 0, 0, 0, false, java.util.List.of()));

        PetChatMessageVO vo = chatService.chat(100L, new PetChatRequest("我们去玩球吧！"));

        assertThat(vo.content()).isEqualTo("好呀好呀！我们一起去玩！");
        assertThat(vo.isAiReply()).isTrue();
    }

    @Test
    @DisplayName("每日聊天限频：超限 429 PET_AI_RATE_LIMITED")
    void dailyLimitExceeded() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        when(valueOperations.increment(anyString())).thenReturn(21L);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> chatService.chat(100L, new PetChatRequest("聊聊天")))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PET_AI_RATE_LIMITED");
    }

    @Test
    @DisplayName("记忆抽取：'我叫XX' 落 owner_nickname 结构化记忆（AUTO 来源）")
    void memoryExtraction() {
        Pet p = pet();
        p.setMemoryExtractEnabled(true);
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(java.util.List.of());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);
        when(aiClient.generateReply(anyString(), anyString())).thenReturn("记住啦！");
        when(contextService.buildContext(any(), any())).thenReturn(new PetContextService.PetContext(
                "小橘", 3, "LIVELY", 80, 90, 70, 80, "空闲中", 0, 0, 0, 0, false, java.util.List.of()));
        when(memoryMapper.insert(any(com.cloudmart.pet.entity.PetMemory.class))).thenReturn(1);

        chatService.chat(100L, new PetChatRequest("我叫小冰，请多关照"));

        org.mockito.ArgumentCaptor<com.cloudmart.pet.entity.PetMemory> captor =
                org.mockito.ArgumentCaptor.forClass(com.cloudmart.pet.entity.PetMemory.class);
        org.mockito.Mockito.verify(memoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getMemoryKey()).isEqualTo("owner_nickname");
        assertThat(captor.getValue().getMemoryValue()).isEqualTo("小冰");
        assertThat(captor.getValue().getSource()).isEqualTo("AUTO");
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("R22 同键重放：回复已存在 → 原样返回，不调 AI、不重复扣额度")
    void keyedReplayReturnsStoredReply() {
        Pet p = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(contextService.buildContext(any(), any())).thenReturn(new PetContextService.PetContext(
                "小橘", 3, "LIVELY", 80, 90, 70, 80, "空闲中", 0, 0, 0, 0, false, java.util.List.of()));
        // 占键撞键 → 查 USER 行与 PET 回复行：都存在 → 重放
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_chat_request_role"));
        com.cloudmart.pet.entity.PetChatMessage existingUser = new com.cloudmart.pet.entity.PetChatMessage();
        existingUser.setSessionId(9L);
        existingUser.setRole(PetChatRole.USER.name());
        existingUser.setContent("你好呀");
        existingUser.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        com.cloudmart.pet.entity.PetChatMessage existingReply = new com.cloudmart.pet.entity.PetChatMessage();
        existingReply.setSessionId(9L);
        existingReply.setRole(PetChatRole.PET.name());
        existingReply.setContent("上次已经回答过啦");
        when(messageMapper.selectOne(any()))
                .thenReturn(existingUser)
                .thenReturn(existingReply);

        PetRequestContext.setIdempotencyKey("req-key-chat-replay-01");
        try {
            PetChatMessageVO vo = chatService.chat(100L, new PetChatRequest("你好呀"));

            assertThat(vo.content()).isEqualTo("上次已经回答过啦");
            verify(aiClient, never()).generateReply(anyString(), anyString());
        } finally {
            PetRequestContext.clear();
        }
    }

    @Test
    @DisplayName("R22 同键在途：USER 行新鲜且无回复 → 409 PET_REQUEST_IN_PROGRESS，不调 AI")
    void keyedInFlightRejected() {
        Pet p = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        when(sessionMapper.selectOne(any())).thenReturn(session());
        // 占键撞键 → USER 行存在且新鲜、无 PET 回复 → 在途
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_chat_request_role"));
        com.cloudmart.pet.entity.PetChatMessage existingUser = new com.cloudmart.pet.entity.PetChatMessage();
        existingUser.setSessionId(9L);
        existingUser.setRole(PetChatRole.USER.name());
        existingUser.setContent("你好呀");
        existingUser.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        when(messageMapper.selectOne(any()))
                .thenReturn(existingUser)
                .thenReturn(null);

        PetRequestContext.setIdempotencyKey("req-key-chat-inflight-1");
        try {
            assertThatThrownBy(() -> chatService.chat(100L, new PetChatRequest("你好呀")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(com.cloudmart.pet.constant.PetErrorCodes.PET_REQUEST_IN_PROGRESS);
        } finally {
            PetRequestContext.clear();
        }
        verify(aiClient, never()).generateReply(anyString(), anyString());
    }

    @Test
    @DisplayName("R22 同键异参：消息内容不同 → 409 PET_IDEMPOTENCY_CONFLICT")
    void keyedDifferentContentRejected() {
        Pet p = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_chat_request_role"));
        com.cloudmart.pet.entity.PetChatMessage existingUser = new com.cloudmart.pet.entity.PetChatMessage();
        existingUser.setSessionId(9L);
        existingUser.setRole(PetChatRole.USER.name());
        existingUser.setContent("完全不同的历史消息");
        existingUser.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        when(messageMapper.selectOne(any())).thenReturn(existingUser);

        PetRequestContext.setIdempotencyKey("req-key-chat-conflict-1");
        try {
            assertThatThrownBy(() -> chatService.chat(100L, new PetChatRequest("你好呀")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(com.cloudmart.pet.constant.PetErrorCodes.PET_IDEMPOTENCY_CONFLICT);
        } finally {
            PetRequestContext.clear();
        }
        verify(aiClient, never()).generateReply(anyString(), anyString());
    }

    @Test
    @DisplayName("BE-04：关闭'允许自动记忆'后，聊天完全不抽取")
    void memoryExtraction_disabled() {
        Pet p = pet();
        p.setMemoryExtractEnabled(false);
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(java.util.List.of());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);
        when(aiClient.generateReply(anyString(), anyString())).thenReturn("好哒");
        when(contextService.buildContext(any(), any())).thenReturn(new PetContextService.PetContext(
                "小橘", 3, "LIVELY", 80, 90, 70, 80, "空闲中", 0, 0, 0, 0, false, java.util.List.of()));

        chatService.chat(100L, new PetChatRequest("我叫小冰，请多关照"));

        org.mockito.Mockito.verify(memoryMapper, org.mockito.Mockito.never())
                .insert(any(com.cloudmart.pet.entity.PetMemory.class));
    }
    @Test
    @org.junit.jupiter.api.DisplayName("PET-14/T36：请求状态查询不依赖当前主宠（切宠后原请求可查）")
    void requestStatusFindsRequestAcrossPets() {
        // 用户有两只宠物的会话（9 与 10），请求键落在另一只宠的会话 10
        com.cloudmart.pet.entity.PetChatSession other = new com.cloudmart.pet.entity.PetChatSession();
        other.setId(10L);
        other.setUserId(100L);
        other.setPetId(2L);
        org.mockito.Mockito.when(sessionMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(session(), other));
        org.mockito.Mockito.when(petService.requireOwnedPet(100L)).thenReturn(pet());
        com.cloudmart.pet.entity.PetChatMessage userRow =
                new com.cloudmart.pet.entity.PetChatMessage();
        userRow.setId(77L);
        userRow.setSessionId(10L);
        userRow.setRequestId("chat-req-cross-pet-01");
        userRow.setRole("USER");
        userRow.setContent("你好");
        userRow.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(5));
        org.mockito.Mockito.when(messageMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(userRow, (com.cloudmart.pet.entity.PetChatMessage) null);

        com.cloudmart.pet.service.PetChatService.PetChatRequestStatusVO status = chatService.requestStatus(100L, "chat-req-cross-pet-01");

        org.assertj.core.api.Assertions.assertThat(status.status()).isEqualTo("PROCESSING");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-14/T35：残留重执行 CAS 抢租约——抢到才重执行，抢不到 409 在途")
    void staleReexecutionRequiresLease() {
        PetRequestContext.setIdempotencyKey("chat-req-stale-0001");
        org.mockito.Mockito.when(sessionMapper.selectOne(any())).thenReturn(session());
        org.mockito.Mockito.when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_claim"));
        // 既有 USER 行：创建已超 60s（残留）
        com.cloudmart.pet.entity.PetChatMessage staleRow = new com.cloudmart.pet.entity.PetChatMessage();
        staleRow.setId(66L);
        staleRow.setSessionId(9L);
        staleRow.setRequestId("chat-req-stale-0001");
        staleRow.setRole("USER");
        staleRow.setContent("你好");
        staleRow.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(120));
        org.mockito.Mockito.when(messageMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(staleRow, (com.cloudmart.pet.entity.PetChatMessage) null);
        org.mockito.Mockito.when(petService.requireOwnedPet(100L)).thenReturn(pet());
        // 租约被其他执行者占用（CAS 0 行）
        org.mockito.Mockito.when(messageMapper.update(any(), any())).thenReturn(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        chatService.chat(100L, new com.cloudmart.pet.dto.PetChatRequest("你好")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(com.cloudmart.pet.constant.PetErrorCodes.PET_REQUEST_IN_PROGRESS);
        PetRequestContext.clear();
    }

}

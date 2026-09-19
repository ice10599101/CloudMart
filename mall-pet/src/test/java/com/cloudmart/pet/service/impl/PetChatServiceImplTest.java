package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.dto.PetChatRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetChatSession;
import com.cloudmart.pet.repository.PetChatMessageMapper;
import com.cloudmart.pet.repository.PetChatSessionMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetMemoryMapper;
import com.cloudmart.pet.service.PetAchievementService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
/**
 * 宠物聊天三层结构测试：危机词拦截 / 固定行为模板 / AI 降级模板（Fail-Open）。
 */
@ExtendWith(MockitoExtension.class)
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
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PetChatServiceImpl chatService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
        TableInfoHelper.initTableInfo(assistant, PetChatSession.class);
    }

    @BeforeEach
    void setUp() {
        chatService = new PetChatServiceImpl(petService, contextService, aiClient, sessionMapper,
                messageMapper, memoryMapper, achievementService, new PetProperties(), redisTemplate);
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
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(messageMapper.insert(any(com.cloudmart.pet.entity.PetChatMessage.class))).thenReturn(1);

        PetChatMessageVO vo = chatService.chat(100L, new PetChatRequest("我不想活了"));

        assertThat(vo.content()).contains("12356");
        assertThat(vo.isAiReply()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(aiClient);
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
    @DisplayName("记忆抽取：'我叫XX' 落 owner_nickname 结构化记忆")
    void memoryExtraction() {
        Pet p = pet();
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
    }
}

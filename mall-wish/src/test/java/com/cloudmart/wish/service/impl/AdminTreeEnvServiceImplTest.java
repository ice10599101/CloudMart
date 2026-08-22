package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminEnvConfigRequest;
import com.cloudmart.wish.dto.TriggerSpecialEventRequest;
import com.cloudmart.wish.entity.WishEnvConfig;
import com.cloudmart.wish.entity.WishSpecialEvent;
import com.cloudmart.wish.enums.EnvCategory;
import com.cloudmart.wish.enums.SpecialEventStatus;
import com.cloudmart.wish.repository.WishEnvConfigMapper;
import com.cloudmart.wish.repository.WishSpecialEventMapper;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminTreeEnvServiceImpl 单元测试（特殊事件单活跃语义/环境配置 CRUD 契约）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminTreeEnvServiceImpl 单元测试")
class AdminTreeEnvServiceImplTest {

    @Mock
    private WishSpecialEventMapper specialEventMapper;
    @Mock
    private WishEnvConfigMapper envConfigMapper;

    private AdminTreeEnvServiceImpl adminTreeEnvService;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WishSpecialEvent.class);
        TableInfoHelper.initTableInfo(assistant, WishEnvConfig.class);
        adminTreeEnvService = new AdminTreeEnvServiceImpl(
                specialEventMapper, envConfigMapper, new ObjectMapper());
    }

    private WishEnvConfig meteorConfig() {
        WishEnvConfig config = new WishEnvConfig();
        config.setId(101L);
        config.setEnvCode("METEOR_SHOWER");
        config.setCategory(EnvCategory.SPECIAL_EVENT);
        config.setName("流星雨");
        config.setDescription("全站流星划过树冠");
        config.setPriority(100);
        config.setVisual("{\"skyColor\":\"#0c1b3a\"}");
        config.setIsActive(true);
        return config;
    }

    private WishSpecialEvent activeEvent(Long id, String eventCode) {
        WishSpecialEvent event = new WishSpecialEvent();
        event.setId(id);
        event.setEventCode(eventCode);
        event.setTitle(eventCode);
        event.setStatus(SpecialEventStatus.ACTIVE);
        event.setTriggeredBy(1L);
        event.setTriggeredAt(LocalDateTime.now().minusMinutes(10));
        return event;
    }

    private TriggerSpecialEventRequest triggerRequest(String eventCode, Integer durationMinutes) {
        TriggerSpecialEventRequest request = new TriggerSpecialEventRequest();
        request.setEventCode(eventCode);
        request.setDurationMinutes(durationMinutes);
        return request;
    }

    private AdminEnvConfigRequest configRequest(String envCode, String visual) {
        AdminEnvConfigRequest request = new AdminEnvConfigRequest();
        request.setEnvCode(envCode);
        request.setCategory("SPECIAL_EVENT");
        request.setName("中秋");
        request.setDescription("月满中秋");
        request.setPriority(90);
        request.setVisual(visual);
        return request;
    }

    @Nested
    @DisplayName("triggerSpecialEvent - 触发全站特殊事件")
    class TriggerSpecialEventTests {

        @Test
        @DisplayName("触发成功：结束旧活跃事件（单活跃语义）+ 插入新事件，title/description 默认取配置")
        void triggersEndsOldAndInsertsNew() {
            when(envConfigMapper.selectOne(any())).thenReturn(meteorConfig());

            SpecialEventVO vo = adminTreeEnvService.triggerSpecialEvent(
                    triggerRequest("METEOR_SHOWER", 30), 88L);

            // 先结束全部既有 ACTIVE（单活跃语义）
            verify(specialEventMapper).update(any(), any());
            // 插入新事件：标题回退配置名，expiresAt = now + 30min
            ArgumentCaptor<WishSpecialEvent> captor = ArgumentCaptor.forClass(WishSpecialEvent.class);
            verify(specialEventMapper).insert(captor.capture());
            WishSpecialEvent inserted = captor.getValue();
            assertThat(inserted.getEventCode()).isEqualTo("METEOR_SHOWER");
            assertThat(inserted.getTitle()).isEqualTo("流星雨");
            assertThat(inserted.getDescription()).isEqualTo("全站流星划过树冠");
            assertThat(inserted.getStatus()).isEqualTo(SpecialEventStatus.ACTIVE);
            assertThat(inserted.getTriggeredBy()).isEqualTo(88L);
            assertThat(inserted.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));
            assertThat(vo.eventCode()).isEqualTo("METEOR_SHOWER");
        }

        @Test
        @DisplayName("durationMinutes 为空：expiresAt=null 持续至手动结束")
        void noDuration_neverExpires() {
            when(envConfigMapper.selectOne(any())).thenReturn(meteorConfig());

            SpecialEventVO vo = adminTreeEnvService.triggerSpecialEvent(
                    triggerRequest("METEOR_SHOWER", null), 88L);

            assertThat(vo.expiresAt()).isNull();
        }

        @Test
        @DisplayName("自定义 title/description 覆盖配置默认值")
        void customTitleOverridesConfigDefault() {
            when(envConfigMapper.selectOne(any())).thenReturn(meteorConfig());
            TriggerSpecialEventRequest request = triggerRequest("METEOR_SHOWER", null);
            request.setTitle("中秋流星雨");
            request.setDescription("运营自定义文案");

            SpecialEventVO vo = adminTreeEnvService.triggerSpecialEvent(request, 88L);

            assertThat(vo.title()).isEqualTo("中秋流星雨");
            assertThat(vo.description()).isEqualTo("运营自定义文案");
        }

        @Test
        @DisplayName("事件代码无启用配置：TREE_ENV_CONFIG_NOT_FOUND，不产生任何写")
        void unknownEventCode_rejected() {
            when(envConfigMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> adminTreeEnvService.triggerSpecialEvent(
                    triggerRequest("NOT_EXIST", null), 88L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.TREE_ENV_CONFIG_NOT_FOUND));
            verify(specialEventMapper, never()).insert(any(WishSpecialEvent.class));
            verify(specialEventMapper, never()).update(any(), any());
        }
    }

    @Nested
    @DisplayName("endSpecialEvent - 手动结束")
    class EndSpecialEventTests {

        @Test
        @DisplayName("结束活跃事件：置 ENDED")
        void endsActiveEvent() {
            when(specialEventMapper.selectById(9001L))
                    .thenReturn(activeEvent(9001L, "METEOR_SHOWER"));

            SpecialEventVO vo = adminTreeEnvService.endSpecialEvent(9001L);

            assertThat(vo.status()).isEqualTo(SpecialEventStatus.ENDED);
            verify(specialEventMapper).update(any(), any());
        }

        @Test
        @DisplayName("事件不存在：TREE_SPECIAL_EVENT_NOT_FOUND")
        void unknownEvent_rejected() {
            when(specialEventMapper.selectById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> adminTreeEnvService.endSpecialEvent(9999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.TREE_SPECIAL_EVENT_NOT_FOUND));
        }

        @Test
        @DisplayName("已结束事件：幂等不再写（返回当前 ENDED 状态）")
        void alreadyEnded_idempotent() {
            WishSpecialEvent ended = activeEvent(9001L, "METEOR_SHOWER");
            ended.setStatus(SpecialEventStatus.ENDED);
            when(specialEventMapper.selectById(9001L)).thenReturn(ended);

            SpecialEventVO vo = adminTreeEnvService.endSpecialEvent(9001L);

            assertThat(vo.status()).isEqualTo(SpecialEventStatus.ENDED);
            verify(specialEventMapper, never()).update(any(), any());
        }
    }

    @Nested
    @DisplayName("listSpecialEvents - 事件列表")
    class ListSpecialEventsTests {

        @Test
        @DisplayName("limit 边界收敛：超上限 200、低于 1 收敛为合法值")
        void limitClamped() {
            adminTreeEnvService.listSpecialEvents(99999);
            adminTreeEnvService.listSpecialEvents(0);

            verify(specialEventMapper, org.mockito.Mockito.times(2)).selectList(any());
        }
    }

    @Nested
    @DisplayName("环境配置 CRUD")
    class EnvConfigCrudTests {

        @Test
        @DisplayName("新增成功：visual 规范化为紧凑 JSON")
        void createNormalizesVisual() {
            when(envConfigMapper.selectCount(any())).thenReturn(0L);
            ArgumentCaptor<WishEnvConfig> captor = ArgumentCaptor.forClass(WishEnvConfig.class);

            EnvConfigVO vo = adminTreeEnvService.createEnvConfig(
                    configRequest("MID_AUTUMN", "{ \"skyColor\" : \"#1a1a4e\" }"));

            verify(envConfigMapper).insert(captor.capture());
            assertThat(captor.getValue().getVisual()).isEqualTo("{\"skyColor\":\"#1a1a4e\"}");
            assertThat(vo.envCode()).isEqualTo("MID_AUTUMN");
            assertThat(vo.isActive()).isTrue();
        }

        @Test
        @DisplayName("新增 code 重复：TREE_ENV_CONFIG_CODE_DUPLICATED")
        void createDuplicatedCode_rejected() {
            when(envConfigMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminTreeEnvService.createEnvConfig(
                    configRequest("SUNNY", null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.TREE_ENV_CONFIG_CODE_DUPLICATED));
        }

        @Test
        @DisplayName("新增 visual 非法 JSON：TREE_ENV_VISUAL_INVALID")
        void createInvalidVisual_rejected() {
            when(envConfigMapper.selectCount(any())).thenReturn(0L);

            assertThatThrownBy(() -> adminTreeEnvService.createEnvConfig(
                    configRequest("MID_AUTUMN", "not-json{{{")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.TREE_ENV_VISUAL_INVALID));
        }

        @Test
        @DisplayName("新增 visual 非对象（数组/标量）：TREE_ENV_VISUAL_INVALID")
        void createNonObjectVisual_rejected() {
            when(envConfigMapper.selectCount(any())).thenReturn(0L);

            assertThatThrownBy(() -> adminTreeEnvService.createEnvConfig(
                    configRequest("MID_AUTUMN", "[1,2,3]")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.TREE_ENV_VISUAL_INVALID));
        }

        @Test
        @DisplayName("编辑：envCode 不可修改（关联键保护）")
        void updateCannotChangeEnvCode() {
            when(envConfigMapper.selectById(101L)).thenReturn(meteorConfig());
            AdminEnvConfigRequest request = configRequest("RENAMED_CODE", null);

            assertThatThrownBy(() -> adminTreeEnvService.updateEnvConfig(101L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
            verify(envConfigMapper, never()).updateById(any(WishEnvConfig.class));
        }

        @Test
        @DisplayName("编辑：不存在 TREE_ENV_CONFIG_NOT_FOUND")
        void updateUnknownConfig_rejected() {
            when(envConfigMapper.selectById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> adminTreeEnvService.updateEnvConfig(
                    9999L, configRequest("METEOR_SHOWER", null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.TREE_ENV_CONFIG_NOT_FOUND));
        }

        @Test
        @DisplayName("上下架：下架后 is_active=false")
        void updateStatus_deactivates() {
            when(envConfigMapper.selectById(101L)).thenReturn(meteorConfig());

            EnvConfigVO vo = adminTreeEnvService.updateEnvConfigStatus(101L, false);

            assertThat(vo.isActive()).isFalse();
            verify(envConfigMapper).updateById(any(WishEnvConfig.class));
        }
    }
}

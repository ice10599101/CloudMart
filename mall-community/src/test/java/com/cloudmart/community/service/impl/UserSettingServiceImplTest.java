package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.community.entity.UserSetting;
import com.cloudmart.community.repository.UserSettingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingServiceImplTest {

    @Mock
    private UserSettingMapper userSettingMapper;

    private UserSettingServiceImpl userSettingService;

    private static final Long USER_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserSetting.class);
    }

    @BeforeEach
    void setUp() {
        userSettingService = new UserSettingServiceImpl(userSettingMapper);
    }

    private UserSetting buildUserSetting(Long id, Long userId, String key, String value) {
        UserSetting setting = new UserSetting();
        setting.setId(id);
        setting.setUserId(userId);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    @Nested
    @DisplayName("getUserSettings")
    class GetUserSettingsTests {

        @Test
        @DisplayName("should return empty map when no settings exist")
        void getUserSettings_noSettings_returnsEmptyMap() {
            when(userSettingMapper.selectList(any())).thenReturn(List.of());

            Map<String, String> result = userSettingService.getUserSettings(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return map of key-value pairs from settings")
        void getUserSettings_hasSettings_returnsMap() {
            UserSetting setting1 = buildUserSetting(1L, USER_ID, "theme", "dark");
            UserSetting setting2 = buildUserSetting(2L, USER_ID, "language", "zh-CN");

            when(userSettingMapper.selectList(any())).thenReturn(List.of(setting1, setting2));

            Map<String, String> result = userSettingService.getUserSettings(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get("theme")).isEqualTo("dark");
            assertThat(result.get("language")).isEqualTo("zh-CN");
        }

        @Test
        @DisplayName("should preserve insertion order via LinkedHashMap")
        void getUserSettings_preservesInsertionOrder() {
            UserSetting s1 = buildUserSetting(1L, USER_ID, "alpha", "1");
            UserSetting s2 = buildUserSetting(2L, USER_ID, "beta", "2");
            UserSetting s3 = buildUserSetting(3L, USER_ID, "gamma", "3");

            when(userSettingMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));

            Map<String, String> result = userSettingService.getUserSettings(USER_ID);

            assertThat(result.keySet()).containsExactly("alpha", "beta", "gamma");
        }
    }

    @Nested
    @DisplayName("updateUserSettings")
    class UpdateUserSettingsTests {

        @Test
        @DisplayName("should update existing setting value")
        void updateUserSettings_existingKey_updatesValue() {
            UserSetting existing = buildUserSetting(1L, USER_ID, "theme", "light");
            when(userSettingMapper.selectOne(any())).thenReturn(existing);

            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("theme", "dark");
            userSettingService.updateUserSettings(USER_ID, settings);

            verify(userSettingMapper).updateById(existing);
            assertThat(existing.getSettingValue()).isEqualTo("dark");
        }

        @Test
        @DisplayName("should insert new setting when key does not exist")
        void updateUserSettings_newKey_insertsSetting() {
            when(userSettingMapper.selectOne(any())).thenReturn(null);

            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("language", "en");
            userSettingService.updateUserSettings(USER_ID, settings);

            verify(userSettingMapper).insert(any(UserSetting.class));
        }

        @Test
        @DisplayName("should skip blank or null keys")
        void updateUserSettings_blankKey_skipsEntry() {
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("", "value1");
            settings.put("   ", "value2");
            settings.put(null, "value3");
            settings.put("valid_key", "valid_value");
            when(userSettingMapper.selectOne(any())).thenReturn(null);

            userSettingService.updateUserSettings(USER_ID, settings);

            verify(userSettingMapper).insert(any(UserSetting.class));
        }

        @Test
        @DisplayName("should store empty string when value is null")
        void updateUserSettings_nullValue_storesEmptyString() {
            when(userSettingMapper.selectOne(any())).thenReturn(null);

            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("notify", null);
            userSettingService.updateUserSettings(USER_ID, settings);

            verify(userSettingMapper).insert(any(UserSetting.class));
        }
    }

    @Nested
    @DisplayName("getSetting")
    class GetSettingTests {

        @Test
        @DisplayName("should return setting value when found")
        void getSetting_found_returnsValue() {
            UserSetting setting = buildUserSetting(1L, USER_ID, "theme", "dark");
            when(userSettingMapper.selectOne(any())).thenReturn(setting);

            String result = userSettingService.getSetting(USER_ID, "theme", "light");

            assertThat(result).isEqualTo("dark");
        }

        @Test
        @DisplayName("should return default value when setting not found")
        void getSetting_notFound_returnsDefault() {
            when(userSettingMapper.selectOne(any())).thenReturn(null);

            String result = userSettingService.getSetting(USER_ID, "theme", "light");

            assertThat(result).isEqualTo("light");
        }
    }

    @Nested
    @DisplayName("getBooleanSetting")
    class GetBooleanSettingTests {

        @Test
        @DisplayName("should return true when value is 'true'")
        void getBooleanSetting_valueTrue_returnsTrue() {
            UserSetting setting = buildUserSetting(1L, USER_ID, "notify", "true");
            when(userSettingMapper.selectOne(any())).thenReturn(setting);

            boolean result = userSettingService.getBooleanSetting(USER_ID, "notify", false);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when value is '1'")
        void getBooleanSetting_valueOne_returnsTrue() {
            UserSetting setting = buildUserSetting(1L, USER_ID, "notify", "1");
            when(userSettingMapper.selectOne(any())).thenReturn(setting);

            boolean result = userSettingService.getBooleanSetting(USER_ID, "notify", false);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when value is 'false'")
        void getBooleanSetting_valueFalse_returnsFalse() {
            UserSetting setting = buildUserSetting(1L, USER_ID, "notify", "false");
            when(userSettingMapper.selectOne(any())).thenReturn(setting);

            boolean result = userSettingService.getBooleanSetting(USER_ID, "notify", true);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return default value when setting not found")
        void getBooleanSetting_notFound_returnsDefault() {
            when(userSettingMapper.selectOne(any())).thenReturn(null);

            boolean result = userSettingService.getBooleanSetting(USER_ID, "notify", true);

            assertThat(result).isTrue();
        }
    }
}

package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminConfigRequest;
import com.cloudmart.admin.dto.AdminConfigResponse;
import com.cloudmart.admin.entity.AdminConfig;
import com.cloudmart.admin.repository.AdminConfigMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminConfigServiceImplTest {

    private AdminConfigMapper adminConfigMapper;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private AdminConfigServiceImpl adminConfigService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminConfig.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminConfigMapper");
            TableInfoHelper.initTableInfo(assistant, AdminConfig.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminConfigMapper = mock(AdminConfigMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        adminConfigService = new AdminConfigServiceImpl(adminConfigMapper, redisTemplate, objectMapper);
    }

    private AdminConfig buildConfig(Long id, String configKey) {
        AdminConfig config = new AdminConfig();
        config.setId(id);
        config.setConfigName("测试配置");
        config.setConfigKey(configKey);
        config.setConfigValue("value");
        config.setConfigType(1);
        config.setRemark(null);
        config.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return config;
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("config exists -> returns response")
        void getById_Exists_ShouldReturnResponse() {
            AdminConfig config = buildConfig(1L, "sys.key");
            when(adminConfigMapper.selectById(1L)).thenReturn(config);

            AdminConfigResponse response = adminConfigService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.configKey()).isEqualTo("sys.key");
            assertThat(response.configName()).isEqualTo("测试配置");
            assertThat(response.configValue()).isEqualTo("value");
            assertThat(response.configType()).isEqualTo(1);
        }

        @Test
        @DisplayName("config not found -> throws CONFIG_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminConfigMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminConfigService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONFIG_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getByKey")
    class GetByKeyTests {

        @Test
        @DisplayName("cache hit -> returns cached response")
        void getByKey_CacheHit_ShouldReturnCachedResponse() throws Exception {
            String cacheKey = "admin:config:sys.key";
            String cachedJson = "{\"id\":1,\"configKey\":\"sys.key\"}";
            AdminConfigResponse cachedResponse = new AdminConfigResponse(
                    1L, "测试配置", "sys.key", "value", 1, null,
                    LocalDateTime.of(2025, 1, 1, 0, 0)
            );

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(cacheKey)).thenReturn(cachedJson);
            when(objectMapper.readValue(cachedJson, AdminConfigResponse.class)).thenReturn(cachedResponse);

            AdminConfigResponse response = adminConfigService.getByKey("sys.key");

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.configKey()).isEqualTo("sys.key");
            verify(adminConfigMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("cache miss -> queries DB and caches result")
        void getByKey_CacheMiss_ShouldQueryDbAndCache() throws Exception {
            String cacheKey = "admin:config:sys.key";
            AdminConfig config = buildConfig(1L, "sys.key");

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(cacheKey)).thenReturn(null);
            when(adminConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
            when(objectMapper.writeValueAsString(any(AdminConfigResponse.class))).thenReturn("{\"id\":1}");

            AdminConfigResponse response = adminConfigService.getByKey("sys.key");

            assertThat(response).isNotNull();
            assertThat(response.configKey()).isEqualTo("sys.key");
            verify(adminConfigMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(valueOps).set(eq(cacheKey), anyString(), any());
        }

        @Test
        @DisplayName("config not found -> throws CONFIG_NOT_FOUND")
        void getByKey_NotFound_ShouldThrowBusinessException() throws Exception {
            String cacheKey = "admin:config:sys.nonexistent";

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(cacheKey)).thenReturn(null);
            when(adminConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> adminConfigService.getByKey("sys.nonexistent"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONFIG_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("unique configKey -> creates config and evicts cache")
        void create_UniqueKey_ShouldCreate() {
            when(adminConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminConfigMapper.insert(any(AdminConfig.class))).thenReturn(1);

            AdminConfigRequest request = new AdminConfigRequest("配置名", "sys.new.key", "value", 1, null);
            adminConfigService.create(request);

            verify(adminConfigMapper).insert(any(AdminConfig.class));
            verify(redisTemplate).delete("admin:config:sys.new.key");
        }

        @Test
        @DisplayName("duplicate configKey -> throws CONFIG_KEY_EXISTS")
        void create_DuplicateKey_ShouldThrowBusinessException() {
            AdminConfig existing = buildConfig(2L, "sys.duplicate");
            when(adminConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            AdminConfigRequest request = new AdminConfigRequest("配置名", "sys.duplicate", "value", 1, null);

            assertThatThrownBy(() -> adminConfigService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONFIG_KEY_EXISTS"));
            verify(adminConfigMapper, never()).insert(any(AdminConfig.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("config exists and unique key -> updates and evicts cache")
        void update_ExistsAndUnique_ShouldUpdate() {
            AdminConfig config = buildConfig(1L, "sys.old.key");
            when(adminConfigMapper.selectById(1L)).thenReturn(config);
            when(adminConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminConfigMapper.updateById(config)).thenReturn(1);

            AdminConfigRequest request = new AdminConfigRequest("新配置", "sys.new.key", "newVal", 2, "备注");
            adminConfigService.update(1L, request);

            verify(adminConfigMapper).updateById(config);
            verify(redisTemplate).delete("admin:config:sys.old.key");
            verify(redisTemplate).delete("admin:config:sys.new.key");
        }

        @Test
        @DisplayName("config not found -> throws CONFIG_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminConfigMapper.selectById(999L)).thenReturn(null);

            AdminConfigRequest request = new AdminConfigRequest("配置名", "sys.key", "value", 1, null);

            assertThatThrownBy(() -> adminConfigService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONFIG_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("config exists -> deletes and evicts cache")
        void delete_Exists_ShouldDelete() {
            AdminConfig config = buildConfig(1L, "sys.key");
            when(adminConfigMapper.selectById(1L)).thenReturn(config);
            when(adminConfigMapper.deleteById(anyLong())).thenReturn(1);

            adminConfigService.delete(1L);

            verify(adminConfigMapper).deleteById(anyLong());
            verify(redisTemplate).delete("admin:config:sys.key");
        }

        @Test
        @DisplayName("config not found -> throws CONFIG_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminConfigMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminConfigService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CONFIG_NOT_FOUND"));
        }
    }
}

package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminDictDataRequest;
import com.cloudmart.admin.dto.AdminDictDataResponse;
import com.cloudmart.admin.entity.AdminDictData;
import com.cloudmart.admin.repository.AdminDictDataMapper;
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
import java.util.List;

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
class AdminDictDataServiceImplTest {

    private AdminDictDataMapper adminDictDataMapper;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private AdminDictDataServiceImpl adminDictDataService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminDictData.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminDictDataMapper");
            TableInfoHelper.initTableInfo(assistant, AdminDictData.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminDictDataMapper = mock(AdminDictDataMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        adminDictDataService = new AdminDictDataServiceImpl(adminDictDataMapper, redisTemplate, objectMapper);
    }

    private AdminDictData buildDictData(Long id, String dictType) {
        AdminDictData data = new AdminDictData();
        data.setId(id);
        data.setDictType(dictType);
        data.setDictSort(1);
        data.setDictLabel("标签");
        data.setDictValue("值");
        data.setCssClass(null);
        data.setListClass(null);
        data.setIsDefault(0);
        data.setStatus(1);
        data.setRemark(null);
        data.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return data;
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("dict data exists -> returns response")
        void getById_Exists_ShouldReturnResponse() {
            AdminDictData data = buildDictData(1L, "sys_status");
            when(adminDictDataMapper.selectById(1L)).thenReturn(data);

            AdminDictDataResponse response = adminDictDataService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.dictType()).isEqualTo("sys_status");
            assertThat(response.dictLabel()).isEqualTo("标签");
            assertThat(response.dictValue()).isEqualTo("值");
        }

        @Test
        @DisplayName("dict data not found -> throws DICT_DATA_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminDictDataMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDictDataService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_DATA_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("listByType")
    class ListByTypeTests {

        @Test
        @DisplayName("cache miss -> queries DB and caches result")
        void listByType_CacheMiss_ShouldQueryDbAndCache() throws Exception {
            String cacheKey = "admin:dict_data:sys_status";
            AdminDictData data = buildDictData(1L, "sys_status");

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(cacheKey)).thenReturn(null);
            when(adminDictDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(data));
            when(objectMapper.writeValueAsString(any())).thenReturn("[{\"id\":1}]");

            List<AdminDictDataResponse> result = adminDictDataService.listByType("sys_status");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).dictType()).isEqualTo("sys_status");
            verify(adminDictDataMapper).selectList(any(LambdaQueryWrapper.class));
            verify(valueOps).set(eq(cacheKey), anyString(), any());
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("valid request -> creates dict data and evicts cache")
        void create_ValidRequest_ShouldCreate() {
            when(adminDictDataMapper.insert(any(AdminDictData.class))).thenReturn(1);

            AdminDictDataRequest request = new AdminDictDataRequest(
                    "sys_status", 1, "启用", "1", null, null, 0, 1, null
            );
            adminDictDataService.create(request);

            verify(adminDictDataMapper).insert(any(AdminDictData.class));
            verify(redisTemplate).delete("admin:dict_data:sys_status");
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("dict data exists -> updates and evicts cache")
        void update_Exists_ShouldUpdate() {
            AdminDictData data = buildDictData(1L, "sys_old");
            when(adminDictDataMapper.selectById(1L)).thenReturn(data);
            when(adminDictDataMapper.updateById(data)).thenReturn(1);

            AdminDictDataRequest request = new AdminDictDataRequest(
                    "sys_new", 2, "禁用", "0", null, null, 0, 0, "备注"
            );
            adminDictDataService.update(1L, request);

            verify(adminDictDataMapper).updateById(data);
            verify(redisTemplate).delete("admin:dict_data:sys_old");
            verify(redisTemplate).delete("admin:dict_data:sys_new");
        }

        @Test
        @DisplayName("dict data not found -> throws DICT_DATA_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminDictDataMapper.selectById(999L)).thenReturn(null);

            AdminDictDataRequest request = new AdminDictDataRequest(
                    "sys_status", 1, "启用", "1", null, null, 0, 1, null
            );

            assertThatThrownBy(() -> adminDictDataService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_DATA_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("dict data exists -> deletes and evicts cache")
        void delete_Exists_ShouldDelete() {
            AdminDictData data = buildDictData(1L, "sys_status");
            when(adminDictDataMapper.selectById(1L)).thenReturn(data);
            when(adminDictDataMapper.deleteById(anyLong())).thenReturn(1);

            adminDictDataService.delete(1L);

            verify(adminDictDataMapper).deleteById(anyLong());
            verify(redisTemplate).delete("admin:dict_data:sys_status");
        }

        @Test
        @DisplayName("dict data not found -> throws DICT_DATA_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminDictDataMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDictDataService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_DATA_NOT_FOUND"));
        }
    }
}

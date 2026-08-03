package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminDictTypeRequest;
import com.cloudmart.admin.dto.AdminDictTypeResponse;
import com.cloudmart.admin.entity.AdminDictType;
import com.cloudmart.admin.repository.AdminDictTypeMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminDictTypeServiceImplTest {

    private AdminDictTypeMapper adminDictTypeMapper;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private AdminConverter adminConverter;
    private AdminDictTypeServiceImpl adminDictTypeService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminDictType.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminDictTypeMapper");
            TableInfoHelper.initTableInfo(assistant, AdminDictType.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminDictTypeMapper = mock(AdminDictTypeMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        adminConverter = mock(AdminConverter.class);
        adminDictTypeService = new AdminDictTypeServiceImpl(adminDictTypeMapper, redisTemplate, objectMapper, adminConverter);
    }

    private AdminDictType buildDictType(Long id, String dictType) {
        AdminDictType entity = new AdminDictType();
        entity.setId(id);
        entity.setDictName("测试字典");
        entity.setDictType(dictType);
        entity.setStatus(1);
        entity.setRemark(null);
        entity.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return entity;
    }

    private AdminDictTypeResponse buildResponse(AdminDictType entity) {
        return new AdminDictTypeResponse(
                entity.getId(),
                entity.getDictName(),
                entity.getDictType(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getCreatedAt()
        );
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("dict type exists -> returns response via AdminConverter")
        void getById_Exists_ShouldReturnResponse() {
            AdminDictType dictType = buildDictType(1L, "sys_status");
            AdminDictTypeResponse expected = buildResponse(dictType);
            when(adminDictTypeMapper.selectById(1L)).thenReturn(dictType);
            when(adminConverter.toDictTypeResponse(dictType)).thenReturn(expected);

            AdminDictTypeResponse response = adminDictTypeService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.dictType()).isEqualTo("sys_status");
            verify(adminConverter).toDictTypeResponse(dictType);
        }

        @Test
        @DisplayName("dict type not found -> throws DICT_TYPE_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminDictTypeMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDictTypeService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_TYPE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("unique dictType -> creates and evicts cache")
        void create_UniqueType_ShouldCreate() {
            when(adminDictTypeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminDictTypeMapper.insert(any(AdminDictType.class))).thenReturn(1);

            AdminDictTypeRequest request = new AdminDictTypeRequest("状态", "sys_status", 1, null);
            adminDictTypeService.create(request);

            verify(adminDictTypeMapper).insert(any(AdminDictType.class));
            verify(redisTemplate).delete("admin:dict_type:sys_status");
        }

        @Test
        @DisplayName("duplicate dictType -> throws DICT_TYPE_EXISTS")
        void create_DuplicateType_ShouldThrowBusinessException() {
            AdminDictType existing = buildDictType(2L, "sys_status");
            when(adminDictTypeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            AdminDictTypeRequest request = new AdminDictTypeRequest("状态", "sys_status", 1, null);

            assertThatThrownBy(() -> adminDictTypeService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_TYPE_EXISTS"));
            verify(adminDictTypeMapper, never()).insert(any(AdminDictType.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("dict type exists and unique key -> updates and evicts cache")
        void update_ExistsAndUnique_ShouldUpdate() {
            AdminDictType dictType = buildDictType(1L, "sys_old");
            when(adminDictTypeMapper.selectById(1L)).thenReturn(dictType);
            when(adminDictTypeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminDictTypeMapper.updateById(dictType)).thenReturn(1);

            AdminDictTypeRequest request = new AdminDictTypeRequest("新名称", "sys_new", 1, "备注");
            adminDictTypeService.update(1L, request);

            verify(adminDictTypeMapper).updateById(dictType);
            verify(redisTemplate).delete("admin:dict_type:sys_old");
            verify(redisTemplate).delete("admin:dict_type:sys_new");
        }

        @Test
        @DisplayName("dict type not found -> throws DICT_TYPE_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminDictTypeMapper.selectById(999L)).thenReturn(null);

            AdminDictTypeRequest request = new AdminDictTypeRequest("名称", "sys_x", 1, null);

            assertThatThrownBy(() -> adminDictTypeService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_TYPE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("dict type exists -> deletes and evicts cache")
        void delete_Exists_ShouldDelete() {
            AdminDictType dictType = buildDictType(1L, "sys_status");
            when(adminDictTypeMapper.selectById(1L)).thenReturn(dictType);
            when(adminDictTypeMapper.deleteById(anyLong())).thenReturn(1);

            adminDictTypeService.delete(1L);

            verify(adminDictTypeMapper).deleteById(anyLong());
            verify(redisTemplate).delete("admin:dict_type:sys_status");
        }

        @Test
        @DisplayName("dict type not found -> throws DICT_TYPE_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminDictTypeMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDictTypeService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DICT_TYPE_NOT_FOUND"));
        }
    }
}

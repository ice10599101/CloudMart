package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminDeptRequest;
import com.cloudmart.admin.entity.AdminDept;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.repository.AdminDeptMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminDeptServiceImplTest {

    private AdminDeptMapper adminDeptMapper;
    private AdminUserMapper adminUserMapper;
    private AdminDeptServiceImpl adminDeptService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AdminDept.class, AdminUser.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.admin.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        adminDeptMapper = mock(AdminDeptMapper.class);
        adminUserMapper = mock(AdminUserMapper.class);
        adminDeptService = new AdminDeptServiceImpl(adminDeptMapper, adminUserMapper);
    }

    private AdminDept buildDept(Long id, Long parentId, String ancestors) {
        AdminDept dept = new AdminDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName("Test Dept");
        dept.setOrderNum(1);
        dept.setStatus(1);
        return dept;
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("dept exists -> returns response")
        void getById_Exists_ShouldReturnResponse() {
            AdminDept dept = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);

            adminDeptService.getById(1L);

            verify(adminDeptMapper).selectById(1L);
        }

        @Test
        @DisplayName("dept not found -> throws DEPT_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminDeptMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDeptService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DEPT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("root dept (parentId=0) -> ancestors = '0'")
        void create_RootDept_ShouldSetAncestorsTo0() {
            AdminDeptRequest request = new AdminDeptRequest(0L, null, "Root Dept", 1, null, null, null, 1);

            adminDeptService.create(request);

            verify(adminDeptMapper).insert(any(AdminDept.class));
        }

        @Test
        @DisplayName("child dept -> ancestors = parent.ancestors + ',' + parentId")
        void create_ChildDept_ShouldCalculateAncestors() {
            AdminDept parent = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(1L)).thenReturn(parent);

            AdminDeptRequest request = new AdminDeptRequest(1L, null, "Child Dept", 2, null, null, null, 1);
            adminDeptService.create(request);

            verify(adminDeptMapper).insert(any(AdminDept.class));
        }

        @Test
        @DisplayName("parent not found -> throws PARENT_DEPT_NOT_FOUND")
        void create_ParentNotFound_ShouldThrowBusinessException() {
            when(adminDeptMapper.selectById(999L)).thenReturn(null);

            AdminDeptRequest request = new AdminDeptRequest(999L, null, "Orphan", 1, null, null, null, 1);

            assertThatThrownBy(() -> adminDeptService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PARENT_DEPT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("dept exists with same parent -> updates without recalculating ancestors")
        void update_SameParent_ShouldUpdateWithoutAncestorRecalc() {
            AdminDept dept = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);

            AdminDeptRequest request = new AdminDeptRequest(0L, null, "Updated Dept", 1, null, null, null, 1);
            adminDeptService.update(1L, request);

            verify(adminDeptMapper).updateById(dept);
        }

        @Test
        @DisplayName("dept exists with changed parent -> recalculates ancestors")
        void update_ChangedParent_ShouldRecalculateAncestors() {
            AdminDept dept = buildDept(2L, 0L, "0");
            AdminDept newParent = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(2L)).thenReturn(dept);
            when(adminDeptMapper.selectById(1L)).thenReturn(newParent);

            AdminDeptRequest request = new AdminDeptRequest(1L, null, "Moved Dept", 1, null, null, null, 1);
            adminDeptService.update(2L, request);

            assertThat(dept.getParentId()).isEqualTo(1L);
            assertThat(dept.getAncestors()).isEqualTo("0,1");
            verify(adminDeptMapper).updateById(dept);
        }

        @Test
        @DisplayName("dept not found -> throws DEPT_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminDeptMapper.selectById(999L)).thenReturn(null);

            AdminDeptRequest request = new AdminDeptRequest(0L, null, "X", 1, null, null, null, 1);

            assertThatThrownBy(() -> adminDeptService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DEPT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("dept with no children and no users -> deletes")
        void delete_NoChildrenNoUsers_ShouldDelete() {
            AdminDept dept = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);
            when(adminDeptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            adminDeptService.delete(1L);

            verify(adminDeptMapper).deleteById(1L);
        }

        @Test
        @DisplayName("dept has children -> throws DEPT_HAS_CHILDREN")
        void delete_HasChildren_ShouldThrowBusinessException() {
            AdminDept dept = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);
            when(adminDeptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            assertThatThrownBy(() -> adminDeptService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DEPT_HAS_CHILDREN"));
        }

        @Test
        @DisplayName("dept has users -> throws DEPT_HAS_USERS")
        void delete_HasUsers_ShouldThrowBusinessException() {
            AdminDept dept = buildDept(1L, 0L, "0");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);
            when(adminDeptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            assertThatThrownBy(() -> adminDeptService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DEPT_HAS_USERS"));
        }

        @Test
        @DisplayName("dept not found -> throws DEPT_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminDeptMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDeptService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("DEPT_NOT_FOUND"));
        }
    }
}

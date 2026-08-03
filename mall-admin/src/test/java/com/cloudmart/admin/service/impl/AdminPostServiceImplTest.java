package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminPostRequest;
import com.cloudmart.admin.entity.AdminPost;
import com.cloudmart.admin.repository.AdminPostMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminPostServiceImplTest {

    private AdminPostMapper adminPostMapper;
    private AdminPostServiceImpl adminPostService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminPost.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminPostMapper");
            TableInfoHelper.initTableInfo(assistant, AdminPost.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminPostMapper = mock(AdminPostMapper.class);
        adminPostService = new AdminPostServiceImpl(adminPostMapper);
    }

    private AdminPost buildPost(Long id, String postCode) {
        AdminPost post = new AdminPost();
        post.setId(id);
        post.setPostCode(postCode);
        post.setPostName("Test Post");
        post.setOrderNum(1);
        post.setStatus(1);
        return post;
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("post exists -> returns response via mapper lookup")
        void getById_Exists_ShouldReturnResponse() {
            AdminPost post = buildPost(1L, "CEO");
            when(adminPostMapper.selectById(1L)).thenReturn(post);

            var response = adminPostService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.postCode()).isEqualTo("CEO");
            verify(adminPostMapper).selectById(1L);
        }

        @Test
        @DisplayName("post not found -> throws POSITION_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminPostMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminPostService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POSITION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("unique postCode -> creates post")
        void create_UniquePostCode_ShouldCreate() {
            when(adminPostMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminPostMapper.insert(any(AdminPost.class))).thenReturn(1);

            AdminPostRequest request = new AdminPostRequest("CTO", "Chief Tech Officer", 2, 1, null);
            adminPostService.create(request);

            verify(adminPostMapper).insert(any(AdminPost.class));
        }

        @Test
        @DisplayName("duplicate postCode -> throws POST_CODE_EXISTS")
        void create_DuplicatePostCode_ShouldThrowBusinessException() {
            AdminPost existing = buildPost(2L, "CEO");
            when(adminPostMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            AdminPostRequest request = new AdminPostRequest("CEO", "CEO", 1, 1, null);

            assertThatThrownBy(() -> adminPostService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POST_CODE_EXISTS"));
            verify(adminPostMapper, never()).insert(any(AdminPost.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("post exists and unique code -> updates post")
        void update_ExistsAndUnique_ShouldUpdate() {
            AdminPost post = buildPost(1L, "CEO");
            when(adminPostMapper.selectById(1L)).thenReturn(post);
            when(adminPostMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AdminPostRequest request = new AdminPostRequest("CEO", "Chief Executive Officer", 1, 1, null);
            adminPostService.update(1L, request);

            assertThat(post.getPostName()).isEqualTo("Chief Executive Officer");
            verify(adminPostMapper).updateById(post);
        }

        @Test
        @DisplayName("post not found -> throws POSITION_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminPostMapper.selectById(999L)).thenReturn(null);

            AdminPostRequest request = new AdminPostRequest("X", "X", 1, 1, null);

            assertThatThrownBy(() -> adminPostService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POSITION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("post exists -> deletes post")
        void delete_Exists_ShouldDelete() {
            AdminPost post = buildPost(1L, "CEO");
            when(adminPostMapper.selectById(1L)).thenReturn(post);

            adminPostService.delete(1L);

            verify(adminPostMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("post not found -> throws POSITION_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminPostMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminPostService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POSITION_NOT_FOUND"));
        }
    }
}

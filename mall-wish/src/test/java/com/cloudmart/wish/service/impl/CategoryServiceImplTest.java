package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateCategoryRequest;
import com.cloudmart.wish.dto.UpdateCategoryRequest;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.repository.WishCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl 单元测试")
class CategoryServiceImplTest {

    @Mock
    private WishCategoryMapper wishCategoryMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private static final Long CATEGORY_ID = 100L;
    private static final String CACHE_KEY = "wish:categories";

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(wishCategoryMapper, redisTemplate);
    }

    @Nested
    @DisplayName("listCategories - 分类字典查询")
    class ListCategoriesTests {

        @Test
        @DisplayName("缓存命中直接返回，不查 DB")
        void listCategories_cacheHit_returnsFromCache() {
            List<CategoryVOStub> cached = List.of(
                    new CategoryVOStub(1L, "study", "学习", "icon.png", 1)
            );
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(cached);

            var result = categoryService.listCategories();

            assertThat(result).hasSize(1);
            verify(wishCategoryMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("缓存未命中查 DB 并回填缓存")
        void listCategories_cacheMiss_queriesDbAndFillsCache() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);

            WishCategory category = new WishCategory();
            category.setId(1L);
            category.setCode("study");
            category.setName("学习");
            category.setIcon("icon.png");
            category.setSort(1);
            when(wishCategoryMapper.selectList(any())).thenReturn(List.of(category));

            var result = categoryService.listCategories();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("学习");
            verify(valueOperations).set(eq(CACHE_KEY), any(), anyLong(), any());
        }

        @Test
        @DisplayName("空缓存（空 List）也查 DB")
        void listCategories_emptyCacheList_queriesDb() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(Collections.emptyList());

            when(wishCategoryMapper.selectList(any())).thenReturn(Collections.emptyList());

            var result = categoryService.listCategories();

            assertThat(result).isEmpty();
            verify(wishCategoryMapper).selectList(any());
        }
    }

    @Nested
    @DisplayName("createCategory - 创建分类")
    class CreateCategoryTests {

        @Test
        @DisplayName("正常创建分类，清空缓存")
        void createCategory_success() {
            when(wishCategoryMapper.selectCount(any())).thenReturn(0L);
            when(wishCategoryMapper.insert(any(WishCategory.class))).thenAnswer(invocation -> {
                WishCategory c = invocation.getArgument(0);
                c.setId(CATEGORY_ID);
                return 1;
            });
            when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

            CreateCategoryRequest request = new CreateCategoryRequest("health", "健康", 2, "icon.png");

            var result = categoryService.createCategory(request);

            assertThat(result.id()).isEqualTo(CATEGORY_ID);
            assertThat(result.code()).isEqualTo("health");
            verify(redisTemplate).delete(CACHE_KEY);
        }

        @Test
        @DisplayName("code 重复时抛出 WISH_VALIDATION_ERROR")
        void createCategory_duplicateCode_throwsException() {
            when(wishCategoryMapper.selectCount(any())).thenReturn(1L);

            CreateCategoryRequest request = new CreateCategoryRequest("study", "学习", null, null);

            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
                    });

            verify(wishCategoryMapper, never()).insert(any(WishCategory.class));
        }
    }

    @Nested
    @DisplayName("updateCategory - 更新分类")
    class UpdateCategoryTests {

        @Test
        @DisplayName("正常更新分类名称")
        void updateCategory_success() {
            WishCategory category = new WishCategory();
            category.setId(CATEGORY_ID);
            category.setCode("study");
            category.setName("旧名称");
            category.setSort(1);
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(category);
            when(wishCategoryMapper.updateById(any(WishCategory.class))).thenReturn(1);

            UpdateCategoryRequest request = new UpdateCategoryRequest("新名称", 2, "new-icon.png");

            var result = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(result.name()).isEqualTo("新名称");
            assertThat(result.sortOrder()).isEqualTo(2);
            verify(redisTemplate).delete(CACHE_KEY);
        }

        @Test
        @DisplayName("分类不存在抛出 WISH_CATEGORY_NOT_FOUND")
        void updateCategory_notFound_throwsException() {
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(null);

            UpdateCategoryRequest request = new UpdateCategoryRequest("新名称", null, null);

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_CATEGORY_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("deleteCategory - 删除分类")
    class DeleteCategoryTests {

        @Test
        @DisplayName("删除自定义分类成功")
        void deleteCategory_customCategory_success() {
            WishCategory category = new WishCategory();
            category.setId(2000L);
            when(wishCategoryMapper.selectById(2000L)).thenReturn(category);
            when(wishCategoryMapper.deleteById(2000L)).thenReturn(1);

            categoryService.deleteCategory(2000L);

            verify(wishCategoryMapper).deleteById(2000L);
            verify(redisTemplate).delete(CACHE_KEY);
        }

        @Test
        @DisplayName("删除系统预设分类抛出 WISH_FORBIDDEN")
        void deleteCategory_systemCategory_throwsException() {
            WishCategory category = new WishCategory();
            category.setId(1000L);
            when(wishCategoryMapper.selectById(1000L)).thenReturn(category);

            assertThatThrownBy(() -> categoryService.deleteCategory(1000L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
                    });

            verify(wishCategoryMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("分类不存在抛出 WISH_CATEGORY_NOT_FOUND")
        void deleteCategory_notFound_throwsException() {
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(null);

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_CATEGORY_NOT_FOUND);
                    });
        }
    }

    /** 测试用 VO 桩（避免依赖 CategoryVO record 反序列化） */
    private record CategoryVOStub(Long id, String code, String name, String icon, Integer sort) {}
}

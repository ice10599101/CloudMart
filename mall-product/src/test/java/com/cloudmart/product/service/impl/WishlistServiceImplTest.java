package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.entity.Wishlist;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import com.cloudmart.product.repository.WishlistMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class WishlistServiceImplTest {

    private WishlistMapper wishlistMapper;
    private ProductMapper productMapper;
    private ProductSkuMapper productSkuMapper;
    private WishlistServiceImpl wishlistService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{Wishlist.class, Product.class, ProductSku.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.product.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        wishlistMapper = mock(WishlistMapper.class);
        productMapper = mock(ProductMapper.class);
        productSkuMapper = mock(ProductSkuMapper.class);
        wishlistService = new WishlistServiceImpl(wishlistMapper, productMapper, productSkuMapper);
    }

    @Nested
    @DisplayName("addToList")
    class AddToListTests {

        @Test
        @DisplayName("product exists and not in wishlist -> adds successfully")
        void addToList_ProductExistsNotInWishlist_ShouldAdd() {
            Product product = new Product();
            product.setId(1L);
            product.setName("Phone");
            when(productMapper.selectById(1L)).thenReturn(product);
            when(wishlistMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            wishlistService.addToList(100L, 1L);

            verify(wishlistMapper).insert(any(Wishlist.class));
        }

        @Test
        @DisplayName("product not found -> throws PRODUCT_NOT_FOUND")
        void addToList_ProductNotFound_ShouldThrowBusinessException() {
            when(productMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> wishlistService.addToList(100L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
            verify(wishlistMapper, never()).insert(any(Wishlist.class));
        }

        @Test
        @DisplayName("already in wishlist -> throws WISHLIST_ALREADY_EXISTS")
        void addToList_AlreadyInWishlist_ShouldThrowBusinessException() {
            Product product = new Product();
            product.setId(1L);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(wishlistMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> wishlistService.addToList(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WISHLIST_ALREADY_EXISTS"));
            verify(wishlistMapper, never()).insert(any(Wishlist.class));
        }
    }

    @Nested
    @DisplayName("removeFromList")
    class RemoveFromListTests {

        @Test
        @DisplayName("wishlist item exists -> removes successfully")
        void removeFromList_Exists_ShouldRemove() {
            when(wishlistMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            wishlistService.removeFromList(100L, 1L);

            verify(wishlistMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("wishlist item not found -> throws WISHLIST_NOT_FOUND")
        void removeFromList_NotFound_ShouldThrowBusinessException() {
            when(wishlistMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> wishlistService.removeFromList(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WISHLIST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("isInWishlist")
    class IsInWishlistTests {

        @Test
        @DisplayName("in wishlist -> returns true")
        void isInWishlist_Exists_ShouldReturnTrue() {
            when(wishlistMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThat(wishlistService.isInWishlist(100L, 1L)).isTrue();
        }

        @Test
        @DisplayName("not in wishlist -> returns false")
        void isInWishlist_NotExists_ShouldReturnFalse() {
            when(wishlistMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThat(wishlistService.isInWishlist(100L, 1L)).isFalse();
        }
    }
}

package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.SkuDTO;
import com.cloudmart.product.entity.Category;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.CategoryMapper;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import com.cloudmart.product.config.BloomFilterInitializer;
import com.cloudmart.product.config.CacheBreakdownGuard;
import com.cloudmart.product.service.EsProductSearchService;
import com.cloudmart.product.service.ProductSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductServiceImpl} covering product retrieval and category update logic.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductConverter productConverter;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache categoryCache;

    @Mock
    private ObjectProvider<EsProductSearchService> esProductSearchServiceProvider;

    @Mock
    private ObjectProvider<ProductSyncService> productSyncServiceProvider;

    @Mock
    private ObjectProvider<BloomFilterInitializer> bloomFilterProvider;

    @Mock
    private ObjectProvider<CacheBreakdownGuard> cacheBreakdownGuardProvider;

    private ProductServiceImpl productService;

    private Product product;
    private ProductSku sku;
    private Category category;
    private ProductDTO productDTO;
    private SkuDTO skuDTO;
    private CategoryDTO categoryDTO;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productMapper, productSkuMapper, categoryMapper,
                productConverter, cacheManager, esProductSearchServiceProvider,
                productSyncServiceProvider, bloomFilterProvider, cacheBreakdownGuardProvider
        );

        lenient().when(cacheManager.getCache("category")).thenReturn(categoryCache);
        lenient().when(esProductSearchServiceProvider.getIfAvailable()).thenReturn(null);
        lenient().when(productSyncServiceProvider.getIfAvailable()).thenReturn(null);
        lenient().when(bloomFilterProvider.getIfAvailable()).thenReturn(null);
        lenient().when(cacheBreakdownGuardProvider.getIfAvailable()).thenReturn(null);

        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setCategoryId(10L);
        product.setBrand("TestBrand");
        product.setMainImage("image.jpg");
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        sku = new ProductSku();
        sku.setId(100L);
        sku.setProductId(1L);
        sku.setSkuCode("SKU-001");
        sku.setAttributes("{\"color\":\"red\"}");
        sku.setPrice(new BigDecimal("99.99"));
        sku.setOriginalPrice(new BigDecimal("129.99"));
        sku.setStock(50);
        sku.setImage("sku-image.jpg");
        sku.setStatus(1);

        category = new Category();
        category.setId(10L);
        category.setName("Electronics");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);

        skuDTO = new SkuDTO(
                100L, 1L, "SKU-001", "{\"color\":\"red\"}",
                new BigDecimal("99.99"), new BigDecimal("129.99"),
                50, "sku-image.jpg", 1
        );

        productDTO = new ProductDTO(
                1L, "Test Product", "Test Description",
                10L, "Electronics", "TestBrand",
                "image.jpg", 1, List.of(skuDTO),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        categoryDTO = new CategoryDTO(10L, "Electronics", 0L, 1, null, 1);
    }

    @Test
    @DisplayName("getProductById returns ProductDTO when product exists")
    void getProductById_WhenProductExists_ShouldReturnProductDTO() {
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productSkuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sku));
        when(categoryMapper.selectById(10L)).thenReturn(category);
        when(productConverter.toDTO(product, List.of(sku), "Electronics")).thenReturn(productDTO);

        ProductDTO result = productService.getProductById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Test Product");
        assertThat(result.categoryName()).isEqualTo("Electronics");
        assertThat(result.skus()).hasSize(1);
        assertThat(result.skus().getFirst().skuCode()).isEqualTo("SKU-001");

        verify(productMapper).selectById(1L);
        verify(productSkuMapper).selectList(any(LambdaQueryWrapper.class));
        verify(categoryMapper).selectById(10L);
        verify(productConverter).toDTO(product, List.of(sku), "Electronics");
    }

    @Test
    @DisplayName("getProductById throws PRODUCT_NOT_FOUND when product does not exist")
    void getProductById_WhenProductNotFound_ShouldThrowBusinessException() {
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("PRODUCT_NOT_FOUND");
                    assertThat(be.getMessage()).isEqualTo("商品不存在");
                });

        verify(productMapper).selectById(999L);
    }

    @Test
    @DisplayName("updateCategory updates fields and returns CategoryDTO when category exists")
    void updateCategory_WhenCategoryExists_ShouldUpdateAndReturnDTO() {
        Category existingCategory = new Category();
        existingCategory.setId(10L);
        existingCategory.setName("Old Name");
        existingCategory.setParentId(0L);
        existingCategory.setSortOrder(1);
        existingCategory.setStatus(1);

        CategoryDTO updatedDTO = new CategoryDTO(10L, "New Name", 2L, 5, null, 0);

        when(categoryMapper.selectById(10L)).thenReturn(existingCategory);
        when(productConverter.toCategoryDTO(existingCategory)).thenReturn(updatedDTO);

        CategoryDTO result = productService.updateCategory(10L, "New Name", 2L, 5, 0);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.parentId()).isEqualTo(2L);
        assertThat(result.sortOrder()).isEqualTo(5);
        assertThat(result.status()).isEqualTo(0);

        assertThat(existingCategory.getName()).isEqualTo("New Name");
        assertThat(existingCategory.getParentId()).isEqualTo(2L);
        assertThat(existingCategory.getSortOrder()).isEqualTo(5);
        assertThat(existingCategory.getStatus()).isEqualTo(0);

        verify(categoryMapper).selectById(10L);
        verify(categoryMapper).updateById(existingCategory);
        verify(productConverter).toCategoryDTO(existingCategory);
    }

    @Test
    @DisplayName("updateCategory throws CATEGORY_NOT_FOUND when category does not exist")
    void updateCategory_WhenCategoryNotFound_ShouldThrowBusinessException() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> productService.updateCategory(999L, "Name", 1L, 0, 1))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("CATEGORY_NOT_FOUND");
                    assertThat(be.getMessage()).isEqualTo("分类不存在");
                });

        verify(categoryMapper).selectById(999L);
    }
}

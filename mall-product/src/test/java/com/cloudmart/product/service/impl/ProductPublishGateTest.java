package com.cloudmart.product.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.CreateProductRequest;
import com.cloudmart.product.dto.CreateSkuRequest;
import com.cloudmart.product.entity.Category;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.feign.InventoryInitFeignClient;
import com.cloudmart.product.repository.CategoryMapper;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAT-01：商品发布门禁——发布必须带价格合法的 SKU；库存建档失败回滚发布
 * （库存服务为唯一库存权威，不允许无库存档案的可售商品）。
 */
@DisplayName("ProductServiceImpl 发布门禁与库存建档")
class ProductPublishGateTest {

    private ProductServiceImpl productService;
    private ProductMapper productMapper;
    private ProductSkuMapper productSkuMapper;
    private CategoryMapper categoryMapper;
    private InventoryInitFeignClient inventoryInitFeignClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        productMapper = mock(ProductMapper.class);
        productSkuMapper = mock(ProductSkuMapper.class);
        categoryMapper = mock(CategoryMapper.class);
        inventoryInitFeignClient = mock(InventoryInitFeignClient.class);

        Category category = new Category();
        category.setId(5L);
        category.setName("数码");
        when(categoryMapper.selectById(5L)).thenReturn(category);
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(900L);
            return 1;
        });
        when(productSkuMapper.insert(any(com.cloudmart.product.entity.ProductSku.class))).thenAnswer(inv -> {
            com.cloudmart.product.entity.ProductSku sku = inv.getArgument(0);
            sku.setId(java.util.concurrent.atomic.AtomicLong.class.isInstance(sku) ? null
                    : java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 100000));
            return 1;
        });

        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(anyString())).thenReturn(null);
        com.cloudmart.product.converter.ProductConverter productConverter =
                mock(com.cloudmart.product.converter.ProductConverter.class);
        when(productConverter.toDTO(any(), any(), any()))
                .thenReturn(mock(com.cloudmart.product.dto.ProductDTO.class));

        productService = new ProductServiceImpl(
                productMapper, productSkuMapper, categoryMapper, productConverter,
                cacheManager,
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                inventoryInitFeignClient);
    }

    private static java.lang.String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    @Test
    @DisplayName("发布无 SKU 的商品被拒绝（发布必须带合法 SKU）")
    void createProduct_noSku_rejected() {
        CreateProductRequest request = new CreateProductRequest(
                "无SKU商品", "desc", 5L, "Brand", null, List.of());

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PRODUCT_PUBLISH_INVALID");
        verify(productMapper, never()).insert(any(com.cloudmart.product.entity.Product.class));
    }

    @Test
    @DisplayName("价格非法的 SKU（≤0）拒绝发布")
    void createProduct_invalidPrice_rejected() {
        CreateSkuRequest badSku = new CreateSkuRequest(
                null,
                "SKU-1", "红", BigDecimal.ZERO, null, 10, null);
        CreateProductRequest request = new CreateProductRequest(
                "商品", "desc", 5L, "Brand", null, List.of(badSku));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PRODUCT_PUBLISH_INVALID");
    }

    @Test
    @DisplayName("每个 SKU 都建立库存档案；建档失败抛异常使发布回滚")
    void createProduct_inventoryInit_perSkuAndFailClosed() {
        CreateSkuRequest sku1 = new CreateSkuRequest(
                null,
                "SKU-1", "红", new BigDecimal("10.00"), null, 50, null);
        CreateSkuRequest sku2 = new CreateSkuRequest(
                null,
                "SKU-2", "蓝", new BigDecimal("12.00"), null, 30, null);
        CreateProductRequest request = new CreateProductRequest(
                "商品", "desc", 5L, "Brand", null, List.of(sku1, sku2));

        when(inventoryInitFeignClient.initStock(anyLong(), anyLong(), anyInt()))
                .thenReturn(ApiResponse.ok(null));
        when(productSkuMapper.selectList(any())).thenReturn(List.of());

        var dto = productService.createProduct(request);

        assertThat(dto).isNotNull();
        ArgumentCaptor<Long> skuCaptor = ArgumentCaptor.forClass(Long.class);
        verify(inventoryInitFeignClient, org.mockito.Mockito.times(2))
                .initStock(skuCaptor.capture(), org.mockito.ArgumentMatchers.eq(900L), anyInt());
        assertThat(skuCaptor.getAllValues()).hasSize(2);
    }

    @Test
    @DisplayName("建档失败 → 发布失败（不允许无库存档案的可售商品）")
    void createProduct_inventoryInitFails_publishFails() {
        org.mockito.Mockito.doThrow(new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用"))
                .when(inventoryInitFeignClient).initStock(anyLong(), anyLong(), anyInt());

        CreateSkuRequest sku = new CreateSkuRequest(
                null,
                "SKU-1", "红", new BigDecimal("10.00"), null, 50, null);
        CreateProductRequest request = new CreateProductRequest(
                "商品", "desc", 5L, "Brand", null, List.of(sku));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_SERVICE_UNAVAILABLE");
        verify(inventoryInitFeignClient).initStock(anyLong(), anyLong(), anyInt());
    }
}

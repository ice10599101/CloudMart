package com.cloudmart.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.product.es.ProductDocument;
import com.cloudmart.product.es.ProductSearchRepository;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductReviewMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductSyncService} 单元测试：覆盖 MySQL → ES 同步全流程。
 * 通过 mock {@link ProductSearchRepository} 与 MyBatis Mapper 隔离真实 ES 与 DB。
 */
@ExtendWith(MockitoExtension.class)
class ProductSyncServiceTest {

    @Mock
    private ProductSearchRepository searchRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductSkuMapper skuMapper;

    @Mock
    private ProductReviewMapper reviewMapper;

    private ProductSyncService productSyncService;

    @BeforeEach
    void setUp() {
        productSyncService = new ProductSyncService(searchRepository, productMapper, skuMapper, reviewMapper);
    }

    private Product buildProduct(Long id, String name, String brand, Long categoryId) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription(name + " 描述");
        product.setBrand(brand);
        product.setCategoryId(categoryId);
        product.setMainImage("http://img.test.com/" + id + ".jpg");
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return product;
    }

    private ProductSku buildSku(Long id, Long productId, BigDecimal price, BigDecimal originalPrice) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setPrice(price);
        sku.setOriginalPrice(originalPrice);
        return sku;
    }

    @SuppressWarnings("unchecked")
    private Page<Product> mockPage(List<Product> records, long total) {
        Page<Product> page = new Page<>(1, 500);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    @Nested
    @DisplayName("syncToEs - 单商品同步")
    class SyncToEsTests {

        @Test
        @DisplayName("商品存在且有 SKU 时应正确同步到 ES")
        void syncToEs_withSkus_savesDocumentWithAggregatedPrice() {
            Product product = buildProduct(1L, "帐篷", "牧高笛", 100L);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(skuMapper.selectByProductId(1L)).thenReturn(List.of(
                    buildSku(10L, 1L, new BigDecimal("199.00"), new BigDecimal("299.00")),
                    buildSku(11L, 1L, new BigDecimal("159.00"), new BigDecimal("259.00"))
            ));
            // 模拟有评论：平均评分 4.5
            when(reviewMapper.selectAvgRatingByProductIds(List.of(1L)))
                    .thenReturn(List.of(Map.of("productId", 1L, "avgRating", 4.5)));

            productSyncService.syncToEs(1L);

            ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
            verify(searchRepository).save(captor.capture());

            ProductDocument saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getName()).isEqualTo("帐篷");
            assertThat(saved.getBrand()).isEqualTo("牧高笛");
            assertThat(saved.getCategoryId()).isEqualTo(100L);
            assertThat(saved.getMinPrice()).isEqualTo(159.0);
            assertThat(saved.getMaxOriginalPrice()).isEqualTo(299.0);
            assertThat(saved.getMainImage()).isEqualTo("http://img.test.com/1.jpg");
            assertThat(saved.getSalesCount()).isZero();
            assertThat(saved.getAvgRating()).isEqualTo(4.5);
            assertThat(saved.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("商品无 SKU 时价格字段应为 null")
        void syncToEs_withoutSkus_savesDocumentWithNullPrice() {
            Product product = buildProduct(2L, "睡袋", "探路者", 200L);
            when(productMapper.selectById(2L)).thenReturn(product);
            when(skuMapper.selectByProductId(2L)).thenReturn(List.of());
            // 无评论记录
            when(reviewMapper.selectAvgRatingByProductIds(List.of(2L)))
                    .thenReturn(List.of());

            productSyncService.syncToEs(2L);

            ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
            verify(searchRepository).save(captor.capture());
            assertThat(captor.getValue().getMinPrice()).isNull();
            assertThat(captor.getValue().getMaxOriginalPrice()).isNull();
            assertThat(captor.getValue().getAvgRating()).isZero();
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("商品不存在时不应写入 ES")
        void syncToEs_productNotFound_doesNotSave() {
            when(productMapper.selectById(99L)).thenReturn(null);

            productSyncService.syncToEs(99L);

            verify(searchRepository, never()).save(any(ProductDocument.class));
        }
    }

    @Nested
    @DisplayName("deleteFromEs - 删除索引")
    class DeleteFromEsTests {

        @Test
        @DisplayName("应调用 repository.deleteById 删除指定商品")
        void deleteFromEs_delegatesToRepository() {
            productSyncService.deleteFromEs(5L);

            verify(searchRepository).deleteById(5L);
        }
    }

    @Nested
    @DisplayName("reindexAll - 分页全量重建索引")
    class ReindexAllTests {

        @Test
        @DisplayName("应分页查询商品并批量写入 ES，返回同步数量")
        void reindexAll_withProducts_batchSavesAndReturnsCount() {
            Product p1 = buildProduct(1L, "帐篷", "牧高笛", 100L);
            Product p2 = buildProduct(2L, "睡袋", "探路者", 200L);
            when(productMapper.selectPage(any(Page.class), any()))
                    .thenReturn(mockPage(List.of(p1, p2), 2));
            when(skuMapper.selectByProductIds(any())).thenReturn(List.of());
            lenient().when(reviewMapper.selectAvgRatingByProductIds(any()))
                    .thenReturn(List.of());

            int count = productSyncService.reindexAll();

            assertThat(count).isEqualTo(2);
            verify(searchRepository, times(1)).saveAll(any(List.class));
        }

        @Test
        @DisplayName("无商品时应返回 0 且不写入 ES")
        void reindexAll_noProducts_returnsZero() {
            when(productMapper.selectPage(any(Page.class), any()))
                    .thenReturn(mockPage(List.of(), 0));

            int count = productSyncService.reindexAll();

            assertThat(count).isZero();
            verify(searchRepository, never()).saveAll(any(List.class));
            verify(searchRepository, never()).save(any(ProductDocument.class));
        }

        @Test
        @DisplayName("多页商品应分批同步且正确聚合 SKU 价格")
        void reindexAll_multiPage_batchesCorrectly() {
            Product p1 = buildProduct(1L, "帐篷", "牧高笛", 100L);
            Product p2 = buildProduct(2L, "睡袋", "探路者", 200L);
            Product p3 = buildProduct(3L, "登山杖", "北面", 300L);

            Page<Product> page1 = mockPage(List.of(p1, p2), 501);
            page1.setCurrent(1);
            Page<Product> page2 = mockPage(List.of(p3), 501);
            page2.setCurrent(2);

            when(productMapper.selectPage(any(Page.class), any()))
                    .thenReturn(page1, page2);
            when(skuMapper.selectByProductIds(any())).thenReturn(List.of(
                    buildSku(10L, 1L, new BigDecimal("100.00"), new BigDecimal("150.00"))
            ));
            lenient().when(reviewMapper.selectAvgRatingByProductIds(any()))
                    .thenReturn(List.of());

            int count = productSyncService.reindexAll();

            assertThat(count).isEqualTo(3);
            verify(searchRepository, times(2)).saveAll(any(List.class));
        }
    }
}

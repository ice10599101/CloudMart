package com.cloudmart.product.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchRequest;
import com.cloudmart.product.dto.ProductSearchResponse;
import com.cloudmart.product.entity.Category;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.es.ProductDocument;
import com.cloudmart.product.repository.CategoryMapper;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.Aggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EsProductSearchService} 单元测试：覆盖搜索查询构建、结果映射、聚合提取与排序逻辑。
 * 通过 mock {@link ElasticsearchOperations} 与 MyBatis Mapper 隔离真实依赖。
 */
@ExtendWith(MockitoExtension.class)
class EsProductSearchServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductConverter productConverter;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SearchHits<ProductDocument> searchHits;

    private EsProductSearchService esProductSearchService;

    @BeforeEach
    void setUp() {
        esProductSearchService = new EsProductSearchService(
                productMapper, productSkuMapper, categoryMapper, productConverter, elasticsearchOperations
        );
    }

    private ProductDocument buildDoc(Long id, String name, Long categoryId, Double minPrice) {
        ProductDocument doc = new ProductDocument();
        doc.setId(id);
        doc.setName(name);
        doc.setCategoryId(categoryId);
        doc.setMinPrice(minPrice);
        doc.setCreatedAt(LocalDateTime.of(2026, 1, id.intValue(), 10, 0));
        return doc;
    }

    @SuppressWarnings("unchecked")
    private void mockSearchHits(List<ProductDocument> docs) {
        List<SearchHit<ProductDocument>> hits = docs.stream()
                .map(d -> {
                    SearchHit<ProductDocument> hit = mock(SearchHit.class);
                    when(hit.getContent()).thenReturn(d);
                    return hit;
                })
                .toList();
        lenient().when(searchHits.getSearchHits()).thenReturn(hits);
        lenient().when(searchHits.getTotalHits()).thenReturn((long) docs.size());
        lenient().when(searchHits.getAggregations()).thenReturn(null);
        when(elasticsearchOperations.search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class)))
                .thenReturn(searchHits);
    }

    /**
     * 模拟 ES 聚合返回：构造品牌与分类的桶聚合结果。
     * 由于泛型通配符原因，使用 thenAnswer 返回 AggregationsContainer。
     */
    private void mockAggregations() {
        // 品牌聚合：牧高笛(5)、北面(3)
        StringTermsBucket brandBucket1 = mock(StringTermsBucket.class);
        when(brandBucket1.key()).thenReturn(FieldValue.of("牧高笛"));
        when(brandBucket1.docCount()).thenReturn(5L);
        StringTermsBucket brandBucket2 = mock(StringTermsBucket.class);
        when(brandBucket2.key()).thenReturn(FieldValue.of("北面"));
        when(brandBucket2.docCount()).thenReturn(3L);
        StringTermsAggregate brandAgg = mock(StringTermsAggregate.class);
        when(brandAgg.buckets()).thenReturn(Buckets.of(b -> b.array(List.of(brandBucket1, brandBucket2))));

        // 分类聚合：100(7)、200(1)
        LongTermsBucket catBucket1 = mock(LongTermsBucket.class);
        when(catBucket1.key()).thenReturn(100L);
        when(catBucket1.docCount()).thenReturn(7L);
        LongTermsBucket catBucket2 = mock(LongTermsBucket.class);
        when(catBucket2.key()).thenReturn(200L);
        when(catBucket2.docCount()).thenReturn(1L);
        LongTermsAggregate catAgg = mock(LongTermsAggregate.class);
        when(catAgg.buckets()).thenReturn(Buckets.of(b -> b.array(List.of(catBucket1, catBucket2))));

        // Aggregate 是 tagged union，调用 sterms()/lterms() 返回具体类型
        Aggregate brandAggregate = mock(Aggregate.class);
        when(brandAggregate.isSterms()).thenReturn(true);
        when(brandAggregate.sterms()).thenReturn(brandAgg);
        Aggregate catAggregate = mock(Aggregate.class);
        when(catAggregate.isLterms()).thenReturn(true);
        when(catAggregate.lterms()).thenReturn(catAgg);

        Aggregation brandSpringAgg = new Aggregation("brands", brandAggregate);
        Aggregation catSpringAgg = new Aggregation("categories", catAggregate);
        ElasticsearchAggregation brandEsAgg = new ElasticsearchAggregation(brandSpringAgg);
        ElasticsearchAggregation catEsAgg = new ElasticsearchAggregation(catSpringAgg);

        ElasticsearchAggregations aggregations = new ElasticsearchAggregations(java.util.Map.of());
        // 通过反射注入 aggregations 字段（绕过构造器对真实 ES Aggregate 的解析）
        try {
            java.lang.reflect.Field aggregationsField = ElasticsearchAggregations.class.getDeclaredField("aggregations");
            aggregationsField.setAccessible(true);
            aggregationsField.set(aggregations, List.of(brandEsAgg, catEsAgg));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to mock ElasticsearchAggregations", e);
        }
        when(searchHits.getAggregations()).thenAnswer(invocation -> aggregations);
    }

    private Product buildProduct(Long id, Long categoryId) {
        Product product = new Product();
        product.setId(id);
        product.setName("商品" + id);
        product.setCategoryId(categoryId);
        return product;
    }

    private Category buildCategory(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    @Nested
    @DisplayName("search - 基础搜索流程")
    class SearchTests {

        @Test
        @DisplayName("ES 无结果时应返回空响应")
        void search_noHits_returnsEmptyResponse() {
            mockSearchHits(List.of());

            ProductSearchResponse result = esProductSearchService.search(
                    new ProductSearchRequest("不存在", null, null, null, null, "relevance", null, 1, 20)
            );

            assertThat(result.products()).isEmpty();
            assertThat(result.total()).isZero();
            assertThat(result.brands()).isEmpty();
            assertThat(result.categories()).isEmpty();
        }

        @Test
        @DisplayName("应将 ES 命中商品映射为 ProductDTO 并返回聚合分面")
        void search_withHits_returnsMappedDTOsAndFacets() {
            ProductDocument doc1 = buildDoc(1L, "帐篷", 100L, 199.0);
            ProductDocument doc2 = buildDoc(2L, "睡袋", 100L, 99.0);
            mockSearchHits(List.of(doc1, doc2));
            mockAggregations();

            Product product1 = buildProduct(1L, 100L);
            Product product2 = buildProduct(2L, 100L);
            when(productMapper.selectBatchIds(any())).thenReturn(List.of(product1, product2));

            Category category = buildCategory(100L, "户外");
            when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));

            ProductSku sku1 = new ProductSku();
            sku1.setProductId(1L);
            sku1.setPrice(new BigDecimal("199.00"));
            ProductSku sku2 = new ProductSku();
            sku2.setProductId(2L);
            sku2.setPrice(new BigDecimal("99.00"));
            when(productSkuMapper.selectList(any())).thenReturn(List.of(sku1, sku2));

            ProductDTO dto1 = new ProductDTO(1L, "帐篷", null, 100L, "户外", null, null, null, List.of(), null);
            ProductDTO dto2 = new ProductDTO(2L, "睡袋", null, 100L, "户外", null, null, null, List.of(), null);
            when(productConverter.toDTO(product1, List.of(sku1), "户外")).thenReturn(dto1);
            when(productConverter.toDTO(product2, List.of(sku2), "户外")).thenReturn(dto2);

            ProductSearchResponse result = esProductSearchService.search(
                    new ProductSearchRequest("露营", null, null, null, null, "relevance", null, 1, 20)
            );

            assertThat(result.products()).hasSize(2);
            assertThat(result.total()).isEqualTo(2);
            assertThat(result.products().getFirst().id()).isEqualTo(1L);
            // 验证聚合分面
            assertThat(result.brands()).hasSize(2);
            assertThat(result.brands().getFirst().brand()).isEqualTo("牧高笛");
            assertThat(result.brands().getFirst().count()).isEqualTo(5L);
            assertThat(result.categories()).hasSize(2);
            assertThat(result.categories().getFirst().categoryId()).isEqualTo(100L);
            assertThat(result.categories().getFirst().count()).isEqualTo(7L);
        }

        @Test
        @DisplayName("ES 命中但 MySQL 已删除的商品应被过滤掉")
        void search_productDeletedInMysql_filteredOut() {
            ProductDocument doc1 = buildDoc(1L, "帐篷", 100L, 199.0);
            mockSearchHits(List.of(doc1));

            when(productMapper.selectBatchIds(any())).thenReturn(List.of());

            ProductSearchResponse result = esProductSearchService.search(
                    new ProductSearchRequest("帐篷", null, null, null, null, "relevance", null, 1, 20)
            );

            assertThat(result.products()).isEmpty();
        }

        @Test
        @DisplayName("brand 过滤参数应正确传递到查询")
        void search_withBrandFilter_executesWithoutError() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest("帐篷", null, "牧高笛", null, null, "relevance", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }
    }

    @Nested
    @DisplayName("search - 排序参数")
    class SortTests {

        @Test
        @DisplayName("price_asc 排序应正确传递")
        void search_priceAscSort_applied() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest(null, null, null, null, null, "price_asc", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }

        @Test
        @DisplayName("price_desc 排序应正确传递")
        void search_priceDescSort_applied() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest(null, null, null, null, null, "price_desc", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }

        @Test
        @DisplayName("sales_desc 排序应正确传递")
        void search_salesDescSort_applied() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest(null, null, null, null, null, "sales_desc", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }

        @Test
        @DisplayName("rating_desc 排序应正确传递")
        void search_ratingDescSort_applied() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest(null, null, null, null, null, "rating_desc", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }

        @Test
        @DisplayName("未知排序参数应回退到默认 _score 降序")
        void search_unknownSort_fallsBackToRelevance() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest(null, null, null, null, null, "unknown_sort", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }
    }

    @Nested
    @DisplayName("search - 过滤条件组合")
    class FilterCombinationTests {

        @Test
        @DisplayName("同时传入关键字、分类、品牌、价格范围时应正确组装查询")
        void search_allFiltersCombined_executesWithoutError() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest(
                            "露营",
                            100L,
                            "牧高笛",
                            new BigDecimal("50"),
                            new BigDecimal("500"),
                            "price_asc",
                            null,
                            1,
                            10
                    )
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }

        @Test
        @DisplayName("空关键字时应仅使用过滤条件")
        void search_emptyKeyword_usesOnlyFilters() {
            mockSearchHits(List.of());

            esProductSearchService.search(
                    new ProductSearchRequest("", 100L, null, null, null, "relevance", null, 1, 20)
            );

            org.mockito.Mockito.verify(elasticsearchOperations)
                    .search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class));
        }
    }
}

package com.cloudmart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.json.JsonData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ES 商品搜索服务：支持字段权重、算分函数、高亮、聚合 facets。
 *
 * <p>核心能力：
 * <ul>
 *   <li>字段权重：name^3 让商品名匹配优先于 description</li>
 *   <li>FunctionScore：综合销量、评分、新品上架时间计算最终分数</li>
 *   <li>搜索高亮：name 字段命中关键词时用 &lt;em&gt; 标签包裹</li>
 *   <li>聚合 facets：返回品牌、分类的桶聚合，供前端侧边栏筛选</li>
 * </ul>
 *
 * <p>权重调优入口：开发者可通过修改本类中的常量调整字段权重与算分因子，
 * 无需改动其他模块。
 */
@Service
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class EsProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsProductSearchService.class);

    // === 权重与算分调优常量（开发人员可在此调整） ===
    /** name 字段权重倍数：商品名命中关键词的相关度放大倍数 */
    private static final double NAME_BOOST = 3.0;
    /** 销量算分因子：salesCount 的 fieldValueFactor.factor */
    private static final double SALES_FACTOR = 1.2;
    /** 评分算分因子：avgRating 的 fieldValueFactor.factor */
    private static final double RATING_FACTOR = 1.5;
    /** 新品衰减尺度：14 天内 created 衰减不明显，超过 14 天分数逐步衰减 */
    private static final String NEW_PRODUCT_SCALE = "14d";
    /** 新品衰减因子：衰减到 0.5 时的距离 */
    private static final double NEW_PRODUCT_DECAY = 0.5;
    /** 默认评分（无评论时兜底） */
    private static final double DEFAULT_RATING = 4.0;
    /** 聚合桶数量上限 */
    private static final int AGG_SIZE = 20;

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CategoryMapper categoryMapper;
    private final ProductConverter productConverter;
    private final org.springframework.data.elasticsearch.core.ElasticsearchOperations elasticsearchOperations;

    public EsProductSearchService(ProductMapper productMapper,
                                   ProductSkuMapper productSkuMapper,
                                   CategoryMapper categoryMapper,
                                   ProductConverter productConverter,
                                   org.springframework.data.elasticsearch.core.ElasticsearchOperations elasticsearchOperations) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.categoryMapper = categoryMapper;
        this.productConverter = productConverter;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * 商品搜索入口：构建 BoolQuery + FunctionScore + 高亮 + 聚合，返回搜索结果与 facets。
     */
    public ProductSearchResponse search(ProductSearchRequest request) {
        Query boolQuery = buildBoolQuery(request);
        Query finalQuery = buildFunctionScoreQuery(boolQuery, request);

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(finalQuery)
                .withPageable(PageRequest.of(request.page() - 1, request.size()))
                .withSort(resolveSort(request.sort()))
                .withHighlightQuery(buildHighlightQuery())
                .withAggregation("brands", Aggregation.of(a -> a.terms(t -> t.field("brand").size(AGG_SIZE))))
                .withAggregation("categories", Aggregation.of(a -> a.terms(t -> t.field("categoryId").size(AGG_SIZE))))
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(searchQuery, ProductDocument.class);

        List<ProductDTO> productDTOs = enrichWithMySQLData(searchHits);
        List<ProductSearchResponse.BrandBucket> brandBuckets = extractBrandAggregation(searchHits);
        List<ProductSearchResponse.CategoryBucket> categoryBuckets = extractCategoryAggregation(searchHits);

        return new ProductSearchResponse(
                productDTOs,
                brandBuckets,
                categoryBuckets,
                searchHits.getTotalHits(),
                request.page(),
                request.size()
        );
    }

    private Query buildBoolQuery(ProductSearchRequest request) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (request.keyword() != null && !request.keyword().isBlank()) {
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .fields("name^" + NAME_BOOST, "description^1")
                    .query(request.keyword())
            ));
        }

        if (request.categoryId() != null) {
            boolBuilder.filter(f -> f.term(t -> t.field("categoryId").value(FieldValue.of(request.categoryId()))));
        }

        if (request.brand() != null && !request.brand().isBlank()) {
            boolBuilder.filter(f -> f.term(t -> t.field("brand").value(FieldValue.of(request.brand()))));
        }

        if (request.minPrice() != null) {
            double minVal = request.minPrice().doubleValue();
            boolBuilder.filter(f -> f.range(r -> r.untyped(u -> u.field("minPrice").gte(JsonData.of(minVal)))));
        }

        if (request.maxPrice() != null) {
            double maxVal = request.maxPrice().doubleValue();
            boolBuilder.filter(f -> f.range(r -> r.untyped(u -> u.field("minPrice").lte(JsonData.of(maxVal)))));
        }

        // 默认只返回上架商品
        boolBuilder.filter(f -> f.term(t -> t.field("status").value(FieldValue.of(1))));

        return Query.of(q -> q.bool(boolBuilder.build()));
    }

    /**
     * 构建 FunctionScore 查询：在 BoolQuery 基础上叠加销量、评分、新品加权。
     * 仅在有关键词搜索时启用算分函数；无关键词时直接用 BoolQuery（按排序字段返回）。
     */
    private Query buildFunctionScoreQuery(Query boolQuery, ProductSearchRequest request) {
        if (request.keyword() == null || request.keyword().isBlank()) {
            return boolQuery;
        }

        return Query.of(q -> q.functionScore(fs -> fs
                .query(boolQuery)
                .functions(
                        FunctionScore.of(fn -> fn.fieldValueFactor(v -> v
                                .field("salesCount")
                                .factor(SALES_FACTOR)
                                .modifier(FieldValueFactorModifier.Log1p)
                                .missing(0.0)
                        )),
                        FunctionScore.of(fn -> fn.fieldValueFactor(v -> v
                                .field("avgRating")
                                .factor(RATING_FACTOR)
                                .modifier(FieldValueFactorModifier.Log1p)
                                .missing(DEFAULT_RATING)
                        )),
                        FunctionScore.of(fn -> fn.gauss(g -> g
                                .date(d -> d
                                        .field("createdAt")
                                        .placement(p -> p
                                                .origin("now")
                                                .scale(Time.of(t -> t.time(NEW_PRODUCT_SCALE)))
                                                .decay(NEW_PRODUCT_DECAY)
                                        )
                                )
                        ))
                )
                .scoreMode(FunctionScoreMode.Sum)
        ));
    }

    /**
     * 构建高亮查询：name 字段命中关键词时用 &lt;em&gt; 标签包裹，前端可直接渲染。
     */
    private HighlightQuery buildHighlightQuery() {
        HighlightFieldParameters params = HighlightFieldParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withNumberOfFragments(0)
                .build();
        HighlightField nameField = new HighlightField("name", params);
        Highlight highlight = new Highlight(List.of(nameField));
        return new HighlightQuery(highlight, ProductDocument.class);
    }

    private Sort resolveSort(String sort) {
        if (sort == null || "relevance".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "_score");
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "minPrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "minPrice");
            case "sales_desc" -> Sort.by(Sort.Direction.DESC, "salesCount");
            case "rating_desc" -> Sort.by(Sort.Direction.DESC, "avgRating");
            case "created" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "_score");
        };
    }

    /**
     * 用 ES 命中的商品 ID 回查 MySQL，补充 SKU、分类名称等 ES 索引未存储的明细。
     */
    private List<ProductDTO> enrichWithMySQLData(SearchHits<ProductDocument> searchHits) {
        List<Long> productIds = searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent().getId())
                .toList();

        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> categoryIds = productMap.values().stream()
                .map(Product::getCategoryId)
                .distinct()
                .toList();
        Map<Long, String> categoryNameMap = categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        Map<Long, List<ProductSku>> skuMap = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, productIds)
        ).stream().collect(Collectors.groupingBy(ProductSku::getProductId));

        return searchHits.getSearchHits().stream()
                .map(hit -> {
                    ProductDocument doc = hit.getContent();
                    Product product = productMap.get(doc.getId());
                    if (product == null) {
                        return null;
                    }
                    List<ProductSku> skus = skuMap.getOrDefault(doc.getId(), List.of());
                    String categoryName = categoryNameMap.get(product.getCategoryId());
                    ProductDTO dto = productConverter.toDTO(product, skus, categoryName);
                    // ES 命中关键词时用高亮版本覆盖 name，前端可直接渲染 <em> 标签
                    Map<String, List<String>> highlightFields = hit.getHighlightFields();
                    List<String> nameHighlights = highlightFields == null ? null : highlightFields.get("name");
                    if (nameHighlights != null && !nameHighlights.isEmpty()) {
                        String highlighted = nameHighlights.get(0);
                        return new ProductDTO(dto.id(), highlighted, dto.description(), dto.categoryId(),
                                dto.categoryName(), dto.brand(), dto.mainImage(), dto.status(),
                                dto.skus(), dto.createdAt());
                    }
                    return dto;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 提取品牌聚合分面：从 ES 聚合结果中读取 brands 桶，转换为 BrandBucket 列表。
     */
    private List<ProductSearchResponse.BrandBucket> extractBrandAggregation(SearchHits<ProductDocument> searchHits) {
        if (searchHits.getAggregations() == null) {
            return List.of();
        }
        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        for (ElasticsearchAggregation agg : aggregations.aggregations()) {
            if ("brands".equals(agg.aggregation().getName())) {
                Aggregate aggregate = agg.aggregation().getAggregate();
                if (!aggregate.isSterms()) {
                    return List.of();
                }
                List<StringTermsBucket> buckets = aggregate.sterms().buckets().array();
                return buckets.stream()
                        .map(b -> new ProductSearchResponse.BrandBucket(b.key().stringValue(), b.docCount()))
                        .toList();
            }
        }
        return List.of();
    }

    /**
     * 提取分类聚合分面：从 ES 聚合结果中读取 categories 桶，转换为 CategoryBucket 列表。
     */
    private List<ProductSearchResponse.CategoryBucket> extractCategoryAggregation(SearchHits<ProductDocument> searchHits) {
        if (searchHits.getAggregations() == null) {
            return List.of();
        }
        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        for (ElasticsearchAggregation agg : aggregations.aggregations()) {
            if ("categories".equals(agg.aggregation().getName())) {
                Aggregate aggregate = agg.aggregation().getAggregate();
                if (!aggregate.isLterms()) {
                    return List.of();
                }
                List<LongTermsBucket> buckets = aggregate.lterms().buckets().array();
                return buckets.stream()
                        .map(b -> new ProductSearchResponse.CategoryBucket(b.key(), b.docCount()))
                        .toList();
            }
        }
        return List.of();
    }
}

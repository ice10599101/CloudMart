package com.cloudmart.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.product.es.ProductDocument;
import com.cloudmart.product.es.ProductSearchRepository;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductReviewMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class ProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncService.class);

    /** 分页大小：每页查询的商品数量，平衡内存占用与数据库往返次数 */
    private static final int REINDEX_PAGE_SIZE = 500;

    private final ProductSearchRepository searchRepository;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductReviewMapper reviewMapper;

    public ProductSyncService(ProductSearchRepository searchRepository,
                              ProductMapper productMapper,
                              ProductSkuMapper skuMapper,
                              ProductReviewMapper reviewMapper) {
        this.searchRepository = searchRepository;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.reviewMapper = reviewMapper;
    }

    /**
     * 单商品同步到 ES。
     */
    public void syncToEs(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("Product not found for ES sync: {}", productId);
            return;
        }
        List<ProductSku> skus = skuMapper.selectByProductId(productId);
        Map<Long, Double> avgRatingMap = batchLoadAvgRatings(List.of(productId));
        ProductDocument doc = convertToDocument(product, skus, avgRatingMap.getOrDefault(productId, 0.0));
        searchRepository.save(doc);
        log.info("Synced product {} to ES", productId);
    }

    /**
     * 从 ES 删除指定商品的文档。
     */
    public void deleteFromEs(Long productId) {
        searchRepository.deleteById(productId);
        log.info("Deleted product {} from ES", productId);
    }

    /**
     * 全量重建索引：分页查询 MySQL 商品，批量同步到 ES。
     *
     * <p>优化点：
     * <ul>
     *   <li>分页查询避免一次性加载全部商品到内存</li>
     *   <li>每页批量查询 SKU 与平均评分（避免 N+1 查询问题）</li>
     *   <li>使用 saveAll 批量写入 ES，减少 HTTP 请求</li>
     * </ul>
     *
     * @return 同步的商品文档数量
     */
    public int reindexAll() {
        int count = 0;
        int totalPages = Integer.MAX_VALUE;

        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            Page<Product> page = productMapper.selectPage(new Page<>(pageNum, REINDEX_PAGE_SIZE), null);
            totalPages = (int) page.getPages();

            List<Product> products = page.getRecords();
            if (products.isEmpty()) {
                break;
            }

            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, List<ProductSku>> skuMap = batchLoadSkus(productIds);
            Map<Long, Double> avgRatingMap = batchLoadAvgRatings(productIds);

            List<ProductDocument> docs = products.stream()
                    .map(p -> convertToDocument(p,
                            skuMap.getOrDefault(p.getId(), List.of()),
                            avgRatingMap.getOrDefault(p.getId(), 0.0)))
                    .toList();

            searchRepository.saveAll(docs);
            count += docs.size();
            log.info("Reindexed page {}/{} ({} docs, total: {})", pageNum, totalPages, docs.size(), count);
        }

        log.info("Reindexed {} products to ES", count);
        return count;
    }

    /**
     * 批量查询多个商品的 SKU，按 productId 分组返回。
     */
    private Map<Long, List<ProductSku>> batchLoadSkus(List<Long> productIds) {
        List<ProductSku> allSkus = skuMapper.selectByProductIds(productIds);
        return allSkus.stream().collect(Collectors.groupingBy(ProductSku::getProductId));
    }

    /**
     * 批量查询多个商品的平均评分，返回 productId -> avgRating 映射。
     * 无评论的商品不会出现在返回结果中，调用方需用默认值兜底。
     */
    private Map<Long, Double> batchLoadAvgRatings(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = reviewMapper.selectAvgRatingByProductIds(productIds);
        Map<Long, Double> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object pidObj = row.get("productId");
            Object avgObj = row.get("avgRating");
            if (pidObj != null && avgObj != null) {
                Long pid = ((Number) pidObj).longValue();
                double avg = ((Number) avgObj).doubleValue();
                result.put(pid, avg);
            }
        }
        return result;
    }

    /**
     * 将 MySQL 商品实体 + SKU 列表 + 平均评分转换为 ES 文档实体。
     *
     * <p>salesCount 暂设为 0：销量数据由 mall-order 模块通过 RocketMQ 事件异步更新，
     * 全量同步阶段不查询订单表（跨模块依赖）。
     */
    private ProductDocument convertToDocument(Product product, List<ProductSku> skus, double avgRating) {
        Double minPrice = skus.isEmpty() ? null : skus.stream()
                .map(ProductSku::getPrice)
                .map(BigDecimal::doubleValue)
                .min(Double::compare)
                .orElse(null);

        Double maxOriginalPrice = skus.isEmpty() ? null : skus.stream()
                .map(ProductSku::getOriginalPrice)
                .map(BigDecimal::doubleValue)
                .max(Double::compare)
                .orElse(null);

        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId());
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setCategoryId(product.getCategoryId());
        doc.setBrand(product.getBrand());
        doc.setMinPrice(minPrice);
        doc.setMaxOriginalPrice(maxOriginalPrice);
        doc.setMainImage(product.getMainImage());
        doc.setCreatedAt(product.getCreatedAt());
        doc.setSalesCount(0L);
        doc.setAvgRating(avgRating);
        doc.setStatus(product.getStatus() == null ? 1 : product.getStatus());
        return doc;
    }
}

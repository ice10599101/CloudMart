package com.cloudmart.product.config;

import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;

import java.util.List;

/**
 * 布隆过滤器防穿透（CAT-01）：商品与 SKU 分开两个过滤器——
 * 商品详情用商品 ID 判定，SKU 维度（加购/秒杀）用 SKU ID 判定。
 * 此前只有 SKU 过滤器却被商品详情使用，商品 ID ≠ SKU ID，正常详情会被误杀。
 *
 * <p>就绪门控：过滤器加载完成（或初始化失败）前一律放行回 DB——
 * "未就绪时误拦截"比"多查一次 DB"严重；已就绪后不存在 ID 才快速拒绝。</p>
 */
@Component
@ConditionalOnProperty(name = "redisson.enabled", havingValue = "true", matchIfMissing = true)
public class BloomFilterInitializer {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterInitializer.class);
    private static final String PRODUCT_BLOOM_FILTER_NAME = "product:product:bloom";
    private static final String SKU_BLOOM_FILTER_NAME = "product:sku:bloom";
    private static final long EXPECTED_INSERTIONS = 1_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final RedissonClient redissonClient;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    /** 就绪标志：加载完成后才允许快速拦截 */
    private volatile boolean productFilterReady = false;
    private volatile boolean skuFilterReady = false;

    public BloomFilterInitializer(RedissonClient redissonClient,
                                  ProductMapper productMapper,
                                  ProductSkuMapper productSkuMapper) {
        this.redissonClient = redissonClient;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    @PostConstruct
    public void initBloomFilters() {
        try {
            RBloomFilter<Long> productFilter = redissonClient.getBloomFilter(PRODUCT_BLOOM_FILTER_NAME);
            if (!productFilter.isExists() || productFilter.count() == 0) {
                productFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
                loadAllProductIds(productFilter);
            }
            productFilterReady = true;
            log.info("Product bloom filter ready, count={}", productFilter.count());
        } catch (Exception e) {
            // 未就绪：详情判定放行回 DB（绝不误杀正常商品）
            log.warn("Product bloom filter init failed, falling back to DB path: {}", e.getMessage());
        }
        try {
            RBloomFilter<Long> skuFilter = redissonClient.getBloomFilter(SKU_BLOOM_FILTER_NAME);
            if (!skuFilter.isExists() || skuFilter.count() == 0) {
                skuFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
                loadAllSkuIds(skuFilter);
            }
            skuFilterReady = true;
            log.info("SKU bloom filter ready, count={}", skuFilter.count());
        } catch (Exception e) {
            log.warn("SKU bloom filter init failed, falling back to DB path: {}", e.getMessage());
        }
    }

    private void loadAllProductIds(RBloomFilter<Long> bloomFilter) {
        List<Long> ids = productMapper.selectList(null)
                .stream().map(Product::getId).toList();
        for (Long id : ids) {
            bloomFilter.add(id);
        }
        log.info("Loaded {} product IDs into bloom filter", ids.size());
    }

    private void loadAllSkuIds(RBloomFilter<Long> bloomFilter) {
        List<Long> skuIds = productSkuMapper.selectList(null)
                .stream().map(ProductSku::getId).toList();
        for (Long skuId : skuIds) {
            bloomFilter.add(skuId);
        }
        log.info("Loaded {} SKU IDs into bloom filter", skuIds.size());
    }

    /**
     * 商品详情防穿透判定：未就绪/查询异常一律放行（调用方回 DB），
     * 就绪后不存在的商品 ID 才拒绝。
     */
    public boolean mightContainProductId(Long productId) {
        if (!productFilterReady) {
            return true;
        }
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(PRODUCT_BLOOM_FILTER_NAME);
            return bloomFilter.contains(productId);
        } catch (Exception e) {
            log.warn("Product bloom filter check failed, allowing request through: {}", e.getMessage());
            return true;
        }
    }

    /** SKU 维度判定（加购/秒杀等） */
    public boolean mightContainSkuId(Long skuId) {
        if (!skuFilterReady) {
            return true;
        }
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(SKU_BLOOM_FILTER_NAME);
            return bloomFilter.contains(skuId);
        } catch (Exception e) {
            log.warn("SKU bloom filter check failed, allowing request through: {}", e.getMessage());
            return true;
        }
    }

    public void addProductId(Long productId) {
        try {
            redissonClient.getBloomFilter(PRODUCT_BLOOM_FILTER_NAME).add(productId);
        } catch (Exception e) {
            log.warn("Failed to add product ID to bloom filter: {}", e.getMessage());
        }
    }

    public void addSkuId(Long skuId) {
        try {
            redissonClient.getBloomFilter(SKU_BLOOM_FILTER_NAME).add(skuId);
        } catch (Exception e) {
            log.warn("Failed to add SKU ID to bloom filter: {}", e.getMessage());
        }
    }
}

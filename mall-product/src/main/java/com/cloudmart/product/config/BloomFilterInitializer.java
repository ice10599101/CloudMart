package com.cloudmart.product.config;

import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.cloudmart.product.repository.ProductSkuMapper;

import java.util.List;

@Component
@ConditionalOnProperty(name = "redisson.enabled", havingValue = "true", matchIfMissing = true)
public class BloomFilterInitializer {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterInitializer.class);
    private static final String SKU_BLOOM_FILTER_NAME = "product:sku:bloom";
    private static final long EXPECTED_INSERTIONS = 1_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final RedissonClient redissonClient;
    private final ProductSkuMapper productSkuMapper;

    public BloomFilterInitializer(RedissonClient redissonClient, ProductSkuMapper productSkuMapper) {
        this.redissonClient = redissonClient;
        this.productSkuMapper = productSkuMapper;
    }

    @PostConstruct
    public void initBloomFilter() {
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(SKU_BLOOM_FILTER_NAME);
            if (!bloomFilter.isExists() || bloomFilter.count() == 0) {
                bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
                loadAllSkuIds(bloomFilter);
                log.info("SKU Bloom filter initialized with expectedInsertions={}, falsePositiveRate={}",
                    EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
            } else {
                log.info("SKU Bloom filter already exists with count={}", bloomFilter.count());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize SKU bloom filter, caching defense degraded: {}", e.getMessage());
        }
    }

    private void loadAllSkuIds(RBloomFilter<Long> bloomFilter) {
        List<Long> skuIds = productSkuMapper.selectList(null)
            .stream()
            .map(sku -> sku.getId())
            .toList();
        for (Long skuId : skuIds) {
            bloomFilter.add(skuId);
        }
        log.info("Loaded {} SKU IDs into bloom filter", skuIds.size());
    }

    public boolean mightContain(Long skuId) {
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(SKU_BLOOM_FILTER_NAME);
            return bloomFilter.contains(skuId);
        } catch (Exception e) {
            log.warn("Bloom filter check failed, allowing request through: {}", e.getMessage());
            return true;
        }
    }

    public void addSkuId(Long skuId) {
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(SKU_BLOOM_FILTER_NAME);
            bloomFilter.add(skuId);
        } catch (Exception e) {
            log.warn("Failed to add SKU ID to bloom filter: {}", e.getMessage());
        }
    }
}

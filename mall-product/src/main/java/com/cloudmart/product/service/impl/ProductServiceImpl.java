package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.dto.CreateProductRequest;
import com.cloudmart.product.dto.CreateSkuRequest;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchRequest;
import com.cloudmart.product.dto.ProductSearchResponse;
import com.cloudmart.product.dto.UpdateProductRequest;
import com.cloudmart.product.entity.Category;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.CategoryMapper;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import com.cloudmart.product.config.BloomFilterInitializer;
import com.cloudmart.product.config.CacheBreakdownGuard;
import com.cloudmart.product.service.EsProductSearchService;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.service.ProductSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);
    private static final String PRODUCT_CACHE = "product";
    private static final String CATEGORY_CACHE = "category";

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CategoryMapper categoryMapper;
    private final ProductConverter productConverter;
    private final CacheManager cacheManager;
    private final ObjectProvider<EsProductSearchService> esProductSearchServiceProvider;
    private final ObjectProvider<ProductSyncService> productSyncServiceProvider;
    private final ObjectProvider<BloomFilterInitializer> bloomFilterProvider;
    private final ObjectProvider<CacheBreakdownGuard> cacheBreakdownGuardProvider;
    private final com.cloudmart.product.feign.InventoryInitFeignClient inventoryInitFeignClient;

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductSkuMapper productSkuMapper,
                              CategoryMapper categoryMapper,
                              ProductConverter productConverter,
                              CacheManager cacheManager,
                              ObjectProvider<EsProductSearchService> esProductSearchServiceProvider,
                              ObjectProvider<ProductSyncService> productSyncServiceProvider,
                              ObjectProvider<BloomFilterInitializer> bloomFilterProvider,
                              ObjectProvider<CacheBreakdownGuard> cacheBreakdownGuardProvider,
                              com.cloudmart.product.feign.InventoryInitFeignClient inventoryInitFeignClient) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.categoryMapper = categoryMapper;
        this.productConverter = productConverter;
        this.cacheManager = cacheManager;
        this.esProductSearchServiceProvider = esProductSearchServiceProvider;
        this.productSyncServiceProvider = productSyncServiceProvider;
        this.bloomFilterProvider = bloomFilterProvider;
        this.cacheBreakdownGuardProvider = cacheBreakdownGuardProvider;
        this.inventoryInitFeignClient = inventoryInitFeignClient;
    }

    @Override
    @Transactional
    public ProductDTO createProduct(CreateProductRequest request) {
        Category category = categoryMapper.selectById(request.categoryId());
        if (category == null) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "分类不存在");
        }

        // CAT-01：发布门禁——商品直接发布（status=1）必须至少带 1 个价格合法的 SKU；
        // 无 SKU 的建档只能以草稿/下架态存在
        if (request.skus() == null || request.skus().isEmpty()
                || request.skus().stream().anyMatch(s -> s.price() == null
                        || s.price().compareTo(java.math.BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("PRODUCT_PUBLISH_INVALID", "发布商品必须包含至少一个价格合法的 SKU");
        }

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategoryId(request.categoryId());
        product.setBrand(request.brand());
        product.setMainImage(request.mainImage());
        product.setStatus(1);
        productMapper.insert(product);

        List<ProductSku> skus = new ArrayList<>();
        for (CreateSkuRequest skuReq : request.skus()) {
            ProductSku sku = new ProductSku();
            sku.setProductId(product.getId());
            sku.setSkuCode(skuReq.skuCode());
            sku.setAttributes(skuReq.attributes());
            sku.setPrice(skuReq.price());
            sku.setOriginalPrice(skuReq.originalPrice());
            sku.setStock(skuReq.stock());
            sku.setImage(skuReq.image());
            sku.setStatus(1);
            skus.add(sku);
        }
        for (ProductSku sku : skus) {
            productSkuMapper.insert(sku);
        }

        // CAT-01：库存建档到库存服务（唯一库存权威）——建档失败抛异常，
        // 与 @Transactional 一起回滚商品/SKU，防止出现无库存档案的可售商品；
        // T08：瞬时故障受控重试（16.2），重试耗尽仍失败才回滚发布
        for (ProductSku sku : skus) {
            initStockWithRetry(sku.getId(), product.getId(),
                    sku.getStock() != null ? sku.getStock() : 0);
        }

        syncToElasticsearch(product.getId());

        // CAT-01：新 ID 分别加入商品/SKU 布隆过滤器
        BloomFilterInitializer bloomFilter = bloomFilterProvider.getIfAvailable();
        if (bloomFilter != null) {
            bloomFilter.addProductId(product.getId());
            for (ProductSku sku : skus) {
                bloomFilter.addSkuId(sku.getId());
            }
        }

        return productConverter.toDTO(product, skus, category.getName());
    }

    @Override
    @SentinelResource(value = "getProductById", blockHandler = "getProductByIdBlockHandler")
    public ProductDTO getProductById(Long id) {
        // CAT-01：防穿透用「商品」过滤器（原误用 SKU 过滤器，商品 ID ≠ SKU ID 会误杀正常详情）
        BloomFilterInitializer bloomFilter = bloomFilterProvider.getIfAvailable();
        if (bloomFilter != null && !bloomFilter.mightContainProductId(id)) {
            log.debug("Bloom filter rejected product ID: {}", id);
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        // 先查缓存
        Cache cache = cacheManager.getCache(PRODUCT_CACHE);
        if (cache != null) {
            ProductDTO cached = cache.get(id, ProductDTO.class);
            if (cached != null) {
                return cached;
            }
        }

        // 缓存未命中，用分布式锁防击穿
        CacheBreakdownGuard guard = cacheBreakdownGuardProvider.getIfAvailable();
        if (guard != null) {
            ProductDTO result = guard.getWithLock("product:" + id, () -> {
                ProductDTO dto = loadProductFromDb(id);
                if (dto != null && cache != null) {
                    cache.put(id, dto);
                }
                return dto;
            });
            if (result != null) {
                return result;
            }
        }

        // 降级：无 Redisson 时直接查 DB
        ProductDTO dto = loadProductFromDb(id);
        if (dto != null && cache != null) {
            cache.put(id, dto);
        }
        return dto;
    }

    @Override
    public List<com.cloudmart.product.vo.SkuBatchItemVO> getSkuBatchInfo(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        List<ProductSku> skus = productSkuMapper.selectByIds(skuIds);
        if (skus.isEmpty()) {
            return List.of();
        }
        java.util.Set<Long> productIds = skus.stream()
                .map(ProductSku::getProductId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        return skus.stream()
                .map(sku -> {
                    Product product = productMap.get(sku.getProductId());
                    // TRADE-01：报价需要权威价格/销售状态/属性——价格以此处 DB 值为准
                    return new com.cloudmart.product.vo.SkuBatchItemVO(
                            sku.getId(), sku.getProductId(),
                            product != null ? product.getName() : null,
                            sku.getImage(),
                            sku.getPrice(), sku.getStatus(), sku.getAttributes());
                })
                .toList();
    }

    private ProductDTO loadProductFromDb(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)
        );

        Category category = categoryMapper.selectById(product.getCategoryId());
        String categoryName = category != null ? category.getName() : null;

        return productConverter.toDTO(product, skus, categoryName);
    }

    @Override
    @Transactional
    @CacheEvict(value = PRODUCT_CACHE, key = "#id")
    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.categoryId() != null) {
            Category category = categoryMapper.selectById(request.categoryId());
            if (category == null) {
                throw new BusinessException("CATEGORY_NOT_FOUND", "分类不存在");
            }
            product.setCategoryId(request.categoryId());
        }
        if (request.brand() != null) {
            product.setBrand(request.brand());
        }
        if (request.mainImage() != null) {
            product.setMainImage(request.mainImage());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }

        productMapper.updateById(product);

        // T08：SKU 身份稳定——增量更新替代全删重建（历史订单/购物车/营销活动按 skuId
        // 引用，重建会使 ID 全变导致库存档案悬空、引用失效）：
        //   请求项带 id → 更新既有 SKU（显式编辑即重新上架）；无 id → 新增（建档库存）；
        //   数据库存在但请求缺失 → 软停用（status=0，不物理删除，购买被 SKU_OFF_SALE 拦截）；
        //   带的 id 不属于本商品 → 拒绝（禁止跨商品重新分配已使用 ID）。
        List<ProductSku> skus;
        if (request.skus() != null) {
            List<ProductSku> existing = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)
            );
            Map<Long, ProductSku> existingById = existing.stream()
                    .collect(java.util.stream.Collectors.toMap(ProductSku::getId, java.util.function.Function.identity()));

            skus = new ArrayList<>();
            for (CreateSkuRequest skuReq : request.skus()) {
                ProductSku sku;
                if (skuReq.id() != null) {
                    sku = existingById.get(skuReq.id());
                    if (sku == null) {
                        throw new BusinessException("PRODUCT_SKU_NOT_FOUND",
                                "SKU 不存在或不属于该商品: " + skuReq.id());
                    }
                    sku.setSkuCode(skuReq.skuCode());
                    sku.setAttributes(skuReq.attributes());
                    sku.setPrice(skuReq.price());
                    sku.setOriginalPrice(skuReq.originalPrice());
                    sku.setStock(skuReq.stock());
                    sku.setImage(skuReq.image());
                    sku.setStatus(1);
                    productSkuMapper.updateById(sku);
                } else {
                    sku = new ProductSku();
                    sku.setProductId(id);
                    sku.setSkuCode(skuReq.skuCode());
                    sku.setAttributes(skuReq.attributes());
                    sku.setPrice(skuReq.price());
                    sku.setOriginalPrice(skuReq.originalPrice());
                    sku.setStock(skuReq.stock());
                    sku.setImage(skuReq.image());
                    sku.setStatus(1);
                    productSkuMapper.insert(sku);
                    // T08：新 SKU 初始化库存建档（幂等；失败随 @Transactional 回滚，
                    // 防止出现无库存档案的可售 SKU；瞬时故障受控重试）
                    initStockWithRetry(sku.getId(), id,
                            sku.getStock() != null ? sku.getStock() : 0);
                }
                skus.add(sku);
            }

            for (ProductSku sku : existing) {
                if (skus.stream().noneMatch(s -> s.getId().equals(sku.getId())) && sku.getStatus() != null && sku.getStatus() == 1) {
                    sku.setStatus(0);
                    productSkuMapper.updateById(sku);
                }
            }

            skus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)
            );
        } else {
            skus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)
            );
        }

        Category category = categoryMapper.selectById(product.getCategoryId());
        String categoryName = category != null ? category.getName() : null;

        syncToElasticsearch(product.getId());

        return productConverter.toDTO(product, skus, categoryName);
    }

    @Override
    @Transactional
    @CacheEvict(value = PRODUCT_CACHE, key = "#id")
    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        productMapper.deleteById(id);

        ProductSyncService syncService = productSyncServiceProvider.getIfAvailable();
        if (syncService != null) {
            try {
                syncService.deleteFromEs(id);
            } catch (Exception e) {
                log.warn("从ES删除商品失败, productId={}: {}", id, e.getMessage());
            }
        }

        evictCategoryCache();
    }

    @Override
    @SentinelResource(value = "searchProducts", blockHandler = "searchProductsBlockHandler")
    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        EsProductSearchService esService = esProductSearchServiceProvider.getIfAvailable();
        if (esService != null) {
            try {
                return esService.search(request);
            } catch (Exception e) {
                log.warn("ES搜索失败，降级到数据库搜索: {}", e.getMessage());
            }
        }
        return searchFromDatabase(request);
    }

    private ProductSearchResponse searchFromDatabase(ProductSearchRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>();

        // 数据库降级路径默认只看上架商品
        wrapper.eq(Product::getStatus, 1);

        if (request.keyword() != null && !request.keyword().isBlank()) {
            // 关键词按空白分词后取 AND 语义（如「戴森 吸尘器」），
            // 避免整串 LIKE 在分词场景漏召回；单 token 行为不变
            String[] tokens = request.keyword().trim().split("\\s+");
            for (String token : tokens) {
                String kw = token;
                wrapper.and(w -> w
                        .like(Product::getName, kw)
                        .or()
                        .like(Product::getDescription, kw)
                );
            }
        }

        if (request.categoryId() != null) {
            wrapper.eq(Product::getCategoryId, request.categoryId());
        }

        if (request.brand() != null && !request.brand().isBlank()) {
            wrapper.eq(Product::getBrand, request.brand());
        }

        if (request.minPrice() != null && request.maxPrice() != null) {
            wrapper.apply("id IN (SELECT product_id FROM product_skus WHERE status = 1 AND price >= {0} AND price <= {1})",
                    request.minPrice(), request.maxPrice());
        } else if (request.minPrice() != null) {
            wrapper.apply("id IN (SELECT product_id FROM product_skus WHERE status = 1 AND price >= {0})",
                    request.minPrice());
        } else if (request.maxPrice() != null) {
            wrapper.apply("id IN (SELECT product_id FROM product_skus WHERE status = 1 AND price <= {0})",
                    request.maxPrice());
        }

        String sort = request.sort() != null ? request.sort() : "relevance";
        switch (sort) {
            case "price_asc" -> wrapper.last("ORDER BY (SELECT MIN(price) FROM product_skus WHERE product_id = products.id AND status = 1) ASC");
            case "price_desc" -> wrapper.last("ORDER BY (SELECT MIN(price) FROM product_skus WHERE product_id = products.id AND status = 1) DESC");
            case "sales_desc" -> wrapper.last("ORDER BY (SELECT COUNT(*) FROM mall_order.order_items oi WHERE oi.product_id = products.id) DESC");
            case "rating_desc" -> wrapper.last("ORDER BY (SELECT AVG(rating) FROM product_reviews WHERE product_id = products.id AND status = 1) DESC");
            default -> wrapper.orderByDesc(Product::getCreatedAt);
        }

        Page<Product> productPage = productMapper.selectPage(
                new Page<Product>(request.page(), request.size()), wrapper
        );

        List<Long> productIds = productPage.getRecords().stream()
                .map(Product::getId).toList();

        Map<Long, List<ProductSku>> skuMap = productIds.isEmpty() ? Map.of() :
                productSkuMapper.selectList(
                        new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, productIds)
                ).stream().collect(Collectors.groupingBy(ProductSku::getProductId));

        List<Long> categoryIds = productPage.getRecords().stream()
                .map(Product::getCategoryId).distinct().toList();
        Map<Long, String> categoryNameMap = categoryIds.isEmpty() ? Map.of() :
                categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));

        List<ProductDTO> dtos = productPage.getRecords().stream()
                .map(product -> {
                    List<ProductSku> skus = skuMap.getOrDefault(product.getId(), List.of());
                    String categoryName = categoryNameMap.get(product.getCategoryId());
                    return productConverter.toDTO(product, skus, categoryName);
                })
                .toList();

        return new ProductSearchResponse(
                dtos,
                List.of(),
                List.of(),
                productPage.getTotal(),
                request.page(),
                request.size()
        );
    }

    @Override
    @Cacheable(value = CATEGORY_CACHE, key = "'all'")
    public List<CategoryDTO> listCategories() {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder)
        );
        return productConverter.toCategoryDTOList(categories);
    }

    @Override
    public CategoryDTO createCategory(String name, Long parentId) {
        Category category = new Category();
        category.setName(name);
        category.setParentId(parentId != null ? parentId : 0L);
        category.setSortOrder(0);
        category.setStatus(1);
        categoryMapper.insert(category);

        evictCategoryCache();

        return productConverter.toCategoryDTO(category);
    }

    @Override
    @CacheEvict(value = CATEGORY_CACHE, key = "'all'")
    public CategoryDTO updateCategory(Long id, String name, Long parentId, Integer sortOrder, Integer status) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "分类不存在");
        }

        category.setName(name);
        if (parentId != null) {
            category.setParentId(parentId);
        }
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }
        if (status != null) {
            category.setStatus(status);
        }
        categoryMapper.updateById(category);

        return productConverter.toCategoryDTO(category);
    }

    @Override
    @CacheEvict(value = CATEGORY_CACHE, key = "'all'")
    public void deleteCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "分类不存在");
        }

        categoryMapper.deleteById(id);
    }

    /** 建档重试语义（T08/16.2）：最多 3 次尝试、线性退避 200ms/400ms，仅瞬时类错误可重试。 */
    private static final int INIT_STOCK_MAX_ATTEMPTS = 3;
    private static final long INIT_STOCK_BACKOFF_BASE_MILLIS = 200L;

    /**
     * 库存建档受控重试：连接抖动/服务短暂不可用/库存锁忙属于瞬时故障，短退避后重试；
     * 其余业务性失败立即上抛（不盲重试）。重试耗尽仍失败抛出末次异常，
     * 由调用方 @Transactional 回滚发布——fail-closed 语义不变。
     * 远端 initStock 幂等（存在即覆盖初始值），重试安全。
     */
    private void initStockWithRetry(Long skuId, Long productId, Integer stock) {
        BusinessException lastFailure = null;
        for (int attempt = 1; attempt <= INIT_STOCK_MAX_ATTEMPTS; attempt++) {
            try {
                inventoryInitFeignClient.initStock(skuId, productId, stock);
                return;
            } catch (BusinessException e) {
                if (!isTransientInitError(e.getCode())) {
                    throw e;
                }
                lastFailure = e;
            } catch (Exception e) {
                lastFailure = new BusinessException("INVENTORY_INIT_FAILED",
                        "库存建档失败: SKU " + skuId + "（" + e.getMessage() + "）");
            }
            if (attempt < INIT_STOCK_MAX_ATTEMPTS) {
                try {
                    // 16.2 backoff：受控重试的线性退避，可中断且恢复中断标志
                    Thread.sleep(INIT_STOCK_BACKOFF_BASE_MILLIS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastFailure;
                }
            }
        }
        throw lastFailure;
    }

    /** 仅瞬时类错误可重试：服务不可用（降级）/库存锁忙；其余业务失败不重试 */
    private boolean isTransientInitError(String errorCode) {
        return "INVENTORY_SERVICE_UNAVAILABLE".equals(errorCode)
                || "INVENTORY_BUSY".equals(errorCode);
    }

    private void syncToElasticsearch(Long productId) {
        ProductSyncService syncService = productSyncServiceProvider.getIfAvailable();
        if (syncService != null) {
            try {
                syncService.syncToEs(productId);
            } catch (Exception e) {
                log.warn("同步商品到ES失败, productId={}: {}", productId, e.getMessage());
            }
        }
    }

    private void evictCategoryCache() {
        Cache categoryCache = cacheManager.getCache(CATEGORY_CACHE);
        if (categoryCache != null) {
            categoryCache.clear();
        }
    }

    @Override
    public long getProductCount() {
        return productMapper.selectCount(null);
    }

    public ProductSearchResponse searchProductsBlockHandler(ProductSearchRequest request, BlockException ex) {
        log.warn("searchProducts blocked by Sentinel: {}", ex.getRule());
        return new ProductSearchResponse(List.of(), List.of(), List.of(), 0L, request.page(), request.size());
    }

    public ProductDTO getProductByIdBlockHandler(Long id, BlockException ex) {
        log.warn("getProductById blocked by Sentinel, id={}: {}", id, ex.getRule());
        throw new BusinessException("PRODUCT_QUERY_LIMITED", "商品查询过于频繁，请稍后再试");
    }

}

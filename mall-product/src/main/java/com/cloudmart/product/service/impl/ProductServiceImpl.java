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

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductSkuMapper productSkuMapper,
                              CategoryMapper categoryMapper,
                              ProductConverter productConverter,
                              CacheManager cacheManager,
                              ObjectProvider<EsProductSearchService> esProductSearchServiceProvider,
                              ObjectProvider<ProductSyncService> productSyncServiceProvider,
                              ObjectProvider<BloomFilterInitializer> bloomFilterProvider,
                              ObjectProvider<CacheBreakdownGuard> cacheBreakdownGuardProvider) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.categoryMapper = categoryMapper;
        this.productConverter = productConverter;
        this.cacheManager = cacheManager;
        this.esProductSearchServiceProvider = esProductSearchServiceProvider;
        this.productSyncServiceProvider = productSyncServiceProvider;
        this.bloomFilterProvider = bloomFilterProvider;
        this.cacheBreakdownGuardProvider = cacheBreakdownGuardProvider;
    }

    @Override
    @Transactional
    public ProductDTO createProduct(CreateProductRequest request) {
        Category category = categoryMapper.selectById(request.categoryId());
        if (category == null) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "分类不存在");
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
        if (request.skus() != null && !request.skus().isEmpty()) {
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
        }

        syncToElasticsearch(product.getId());

        // 将新 SKU ID 加入布隆过滤器
        BloomFilterInitializer bloomFilter = bloomFilterProvider.getIfAvailable();
        if (bloomFilter != null) {
            for (ProductSku sku : skus) {
                bloomFilter.addSkuId(sku.getId());
            }
        }

        return productConverter.toDTO(product, skus, category.getName());
    }

    @Override
    @SentinelResource(value = "getProductById", blockHandler = "getProductByIdBlockHandler")
    public ProductDTO getProductById(Long id) {
        // 布隆过滤器防穿透：快速拦截不存在的商品 ID
        BloomFilterInitializer bloomFilter = bloomFilterProvider.getIfAvailable();
        if (bloomFilter != null && !bloomFilter.mightContain(id)) {
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

        List<ProductSku> skus;
        if (request.skus() != null) {
            productSkuMapper.delete(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)
            );

            skus = new ArrayList<>();
            for (CreateSkuRequest skuReq : request.skus()) {
                ProductSku sku = new ProductSku();
                sku.setProductId(id);
                sku.setSkuCode(skuReq.skuCode());
                sku.setAttributes(skuReq.attributes());
                sku.setPrice(skuReq.price());
                sku.setOriginalPrice(skuReq.originalPrice());
                sku.setStock(skuReq.stock());
                sku.setImage(skuReq.image());
                sku.setStatus(1);
                productSkuMapper.insert(sku);
                skus.add(sku);
            }
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

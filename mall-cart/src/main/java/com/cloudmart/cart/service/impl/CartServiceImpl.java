package com.cloudmart.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.cart.dto.AddCartItemRequest;
import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.dto.CartItemDTO;
import com.cloudmart.cart.dto.UpdateCartItemRequest;
import com.cloudmart.cart.entity.CartItem;
import com.cloudmart.cart.feign.ProductFeignClient;
import com.cloudmart.cart.feign.ProductFeignClient.ProductInfo;
import com.cloudmart.cart.feign.ProductFeignClient.SkuInfo;
import com.cloudmart.cart.repository.CartItemMapper;
import com.cloudmart.cart.service.CartService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);
    private static final String CART_KEY_PREFIX = "cart:user:";

    private final StringRedisTemplate redisTemplate;
    private final CartItemMapper cartItemMapper;
    private final ObjectMapper objectMapper;
    private final ProductFeignClient productFeignClient;

    public CartServiceImpl(StringRedisTemplate redisTemplate,
                           CartItemMapper cartItemMapper,
                           ObjectMapper objectMapper,
                           ProductFeignClient productFeignClient) {
        this.redisTemplate = redisTemplate;
        this.cartItemMapper = cartItemMapper;
        this.objectMapper = objectMapper;
        this.productFeignClient = productFeignClient;
    }

    @Override
    @SentinelResource(value = "getCart", fallback = "getCartFallback")
    public CartDTO getCart(Long userId) {
        String key = buildKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        // CART-01：缓存空且 DB 有行（Redis 被清/过期/重启）→ 从权威回源重建
        if (entries.isEmpty() && cartItemMapper.selectCount(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId)) > 0) {
            warmCacheFromDb(userId, key);
            entries = redisTemplate.opsForHash().entries(key);
        }

        List<CartItemDTO> items = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalPrice = BigDecimal.ZERO;
        Map<Long, ProductInfo> productInfoCache = new java.util.HashMap<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                CartItemDTO item = deserializeItem(entry.getValue().toString(), userId, entry.getKey());
                if (item != null) {
                    // 缓存行可能来自 DB 回源（不含商品快照），读取路径补齐后回写
                    if (item.price() == null || item.productName() == null) {
                        CartItemDTO enriched = enrichItem(item, productInfoCache);
                        if (enriched != item) {
                            item = enriched;
                            serializeAndPut(key, entry.getKey().toString(), item, userId);
                        }
                    }
                    items.add(item);
                    if (item.checked() != null && item.checked() == 1) {
                        totalQuantity += item.quantity();
                        if (item.price() != null) {
                            totalPrice = totalPrice.add(item.price().multiply(BigDecimal.valueOf(item.quantity())));
                        }
                    }
                }
            } catch (JacksonException e) {
                log.error("反序列化购物车项失败, userId={}, skuId={}", userId, entry.getKey(), e);
            }
        }

        // T08：失效项标记（下架/删除/变价）——展示层提前可见，前端据此禁选；
        // 勾选结算的强校验兜底在 mall-order（SKU_OFF_SALE/价格权威覆盖/STOCK_INSUFFICIENT）
        markInvalidItems(items);

        return new CartDTO(items, totalQuantity, totalPrice);
    }

    @Override
    @SentinelResource(value = "addToCart", fallback = "addToCartFallback")
    public CartItemDTO addItem(Long userId, AddCartItemRequest request) {
        String key = buildKey(userId);
        String skuField = request.skuId().toString();

        ProductInfo productInfo = fetchProductInfo(request.productId());
        // CART-01：SKU 必须属于该商品且可售——商品获取失败/无该 SKU/无有效价格一律拒绝，
        // 不再写 price=null 的坏行
        if (productInfo == null || productInfo.skus() == null) {
            throw new BusinessException("CART_PRODUCT_UNAVAILABLE", "商品信息不可用，无法加入购物车");
        }
        SkuInfo matchedSku = productInfo.skus().stream()
                .filter(s -> s.id().equals(request.skuId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("CART_SKU_MISMATCH", "SKU 与商品不匹配"));
        if (matchedSku.price() == null) {
            throw new BusinessException("CART_SKU_NOT_SELLABLE", "该商品暂不可售");
        }

        // CART-01：DB 权威原子增量（UNIQUE(user_id,sku_id) + ON DUPLICATE KEY 累加），
        // 并发加购不丢增量；Redis 仅作缓存同步
        cartItemMapper.upsertIncrement(userId, request.productId(), request.skuId(), request.quantity());
        CartItem entity = cartItemMapper.findByUserAndSku(userId, request.skuId());

        CartItemDTO item = new CartItemDTO(
                entity.getId(), userId, entity.getProductId(), entity.getSkuId(),
                entity.getQuantity(), entity.getChecked(),
                productInfo.name(), matchedSku.image(), matchedSku.attributes(), matchedSku.price()
        );

        serializeAndPut(key, skuField, item, userId);
        return item;
    }

    @Override
    public CartItemDTO updateItem(Long userId, Long skuId, UpdateCartItemRequest request) {
        // CART-01：以 DB 权威行为基准（缓存缺失也能改），更新后同步缓存
        CartItem entity = cartItemMapper.findByUserAndSku(userId, skuId);
        if (entity == null) {
            throw new BusinessException("CART_ITEM_NOT_FOUND", "购物车项不存在");
        }

        if (request.quantity() != null) {
            if (request.quantity() < 1 || request.quantity() > 999) {
                throw new BusinessException("INVALID_QUANTITY", "数量必须为 1-999");
            }
            entity.setQuantity(request.quantity());
        }
        if (request.checked() != null) {
            entity.setChecked(request.checked());
        }
        cartItemMapper.updateById(entity);

        CartItemDTO cached = findCachedItem(userId, skuId);
        CartItemDTO updated = new CartItemDTO(
                entity.getId(), userId, entity.getProductId(), entity.getSkuId(),
                entity.getQuantity(), entity.getChecked(),
                cached != null ? cached.productName() : null,
                cached != null ? cached.skuImage() : null,
                cached != null ? cached.skuAttributes() : null,
                cached != null ? cached.price() : null
        );
        if (updated.price() == null || updated.productName() == null) {
            updated = enrichItem(updated, new java.util.HashMap<>());
        }
        serializeAndPut(buildKey(userId), skuId.toString(), updated, userId);
        return updated;
    }

    /** 缓存中查找单项（不存在返回 null） */
    private CartItemDTO findCachedItem(Long userId, Long skuId) {
        Object existing = redisTemplate.opsForHash().get(buildKey(userId), skuId.toString());
        if (existing == null) {
            return null;
        }
        try {
            return deserializeItem(existing.toString(), userId, skuId.toString());
        } catch (JacksonException e) {
            return null;
        }
    }

    @Override
    public void removeItem(Long userId, Long skuId) {
        int deleted = cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getSkuId, skuId));
        if (deleted == 0) {
            throw new BusinessException("CART_ITEM_NOT_FOUND", "购物车项不存在");
        }
        redisTemplate.opsForHash().delete(buildKey(userId), skuId.toString());
    }

    @Override
    public void clearCart(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        redisTemplate.delete(buildKey(userId));
    }

    @Override
    public void clearCheckedItems(Long userId) {
        // CART-01：清理权威行（勾选态以 DB 为准）；先读勾选集合再按集合删除，
        // 缩小与并发改动的竞态窗口，不动无关行
        List<CartItem> checked = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getChecked, 1));
        if (checked.isEmpty()) {
            return;
        }
        List<Long> skuIds = checked.stream().map(CartItem::getSkuId).toList();
        cartItemMapper.deleteCheckedBySkus(userId, skuIds);
        redisTemplate.opsForHash().delete(buildKey(userId),
                skuIds.stream().map(String::valueOf).toArray());
    }

    @Override
    public void clearCheckedBySkus(Long userId, java.util.List<Long> skuIds) {
        // T03：只清本次订单实购的 sku 行（勾选态仍以 DB 为准），并发勾选改动不影响其他商品
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        java.util.List<Long> distinct = skuIds.stream().distinct().toList();
        cartItemMapper.deleteCheckedBySkus(userId, distinct);
        redisTemplate.opsForHash().delete(buildKey(userId),
                distinct.stream().map(String::valueOf).toArray());
    }

    @Override
    public void syncToDatabase(Long userId) {
        // CART-01：DB 已是权威——本方法语义变更为「从权威重建缓存」
        //（原"先删后插同步"会在并发下丢行，已废弃；CartSyncTask 调用点保留为缓存预热）
        warmCacheFromDb(userId, buildKey(userId));
    }

    /** 从 DB 权威重建 Redis 缓存（缓存空/损坏时自愈） */
    private void warmCacheFromDb(Long userId, String key) {
        List<CartItem> rows = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        if (rows.isEmpty()) {
            return;
        }
        for (CartItem entity : rows) {
            try {
                CartItemDTO item = new CartItemDTO(
                        entity.getId(), userId, entity.getProductId(), entity.getSkuId(),
                        entity.getQuantity(), entity.getChecked(),
                        null, null, null, null
                );
                redisTemplate.opsForHash().put(key, entity.getSkuId().toString(),
                        objectMapper.writeValueAsString(item));
            } catch (JacksonException e) {
                log.warn("购物车缓存重建失败, userId={}, skuId={}", userId, entity.getSkuId(), e);
            }
        }
    }

    private ProductInfo fetchProductInfo(Long productId) {
        try {
            ApiResponse<ProductInfo> response = productFeignClient.getProductById(productId);
            if (response != null && response.success() && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.warn("获取商品信息失败, productId={}: {}", productId, e.getMessage());
        }
        return null;
    }

    private CartItemDTO deserializeItem(String json, Long userId, Object skuKey) throws JacksonException {
        return objectMapper.readValue(json, CartItemDTO.class);
    }

    /**
     * 补齐缓存行缺失的商品快照（名称/图片/属性/价格）。DB 权威行只存
     * user/product/sku/quantity/checked，回源重建的缓存不含快照；商品已下架或
     * 商品服务不可用时按原样返回，不阻塞购物车读取。
     */
    private CartItemDTO enrichItem(CartItemDTO item, Map<Long, ProductInfo> productInfoCache) {
        ProductInfo productInfo = productInfoCache.computeIfAbsent(item.productId(), this::fetchProductInfo);
        if (productInfo == null || productInfo.skus() == null) {
            return item;
        }
        SkuInfo matchedSku = productInfo.skus().stream()
                .filter(s -> s.id().equals(item.skuId()))
                .findFirst()
                .orElse(null);
        String skuImage = matchedSku != null ? matchedSku.image() : null;
        if (skuImage == null) {
            skuImage = productInfo.mainImage();
        }
        return new CartItemDTO(item.id(), item.userId(), item.productId(), item.skuId(),
                item.quantity(), item.checked(),
                productInfo.name(), skuImage,
                matchedSku != null ? matchedSku.attributes() : null,
                matchedSku != null ? matchedSku.price() : null);
    }

    /**
     * T08：批量核销购物车项与商品权威状态——单次 getSkusBatch 调用，逐项打失效标。
     * 商品服务不可用/返回失败时 fail-open 不打标：浏览路径不被基础设施故障拖垮，
     * 误结算由下单侧 SKU_OFF_SALE/价格覆盖/库存校验兜底，此处只负责"提前可见"。
     */
    private void markInvalidItems(List<CartItemDTO> items) {
        if (items.isEmpty()) {
            return;
        }
        List<Long> skuIds = items.stream().map(CartItemDTO::skuId).distinct().toList();
        ApiResponse<List<Map<String, Object>>> response;
        try {
            response = productFeignClient.getSkusBatch(skuIds);
        } catch (Exception e) {
            log.warn("购物车失效项校验跳过（商品服务不可用）: {}", e.getMessage());
            return;
        }
        if (response == null || !response.success() || response.data() == null) {
            return;
        }
        Map<Long, Map<String, Object>> skuMap = new java.util.HashMap<>();
        for (Map<String, Object> sku : response.data()) {
            if (sku.get("skuId") instanceof Number n) {
                skuMap.put(n.longValue(), sku);
            }
        }
        for (int i = 0; i < items.size(); i++) {
            CartItemDTO item = items.get(i);
            String reason = invalidReasonOf(skuMap.get(item.skuId()), item);
            if (reason != null) {
                items.set(i, new CartItemDTO(item.id(), item.userId(), item.productId(), item.skuId(),
                        item.quantity(), item.checked(), item.productName(), item.skuImage(),
                        item.skuAttributes(), item.price(), true, reason));
            }
        }
    }

    /** 失效判定：SKU 已删 / 已下架 / 无有效价格 / 快照价与权威价不一致；有效返回 null */
    private String invalidReasonOf(Map<String, Object> sku, CartItemDTO item) {
        if (sku == null) {
            return "商品已失效，请移除";
        }
        if (!Integer.valueOf(1).equals(sku.get("status"))) {
            return "商品已下架";
        }
        BigDecimal livePrice = toBigDecimalQuietly(sku.get("price"));
        if (livePrice == null) {
            return "商品暂不可售";
        }
        if (item.price() != null && livePrice.compareTo(item.price()) != 0) {
            return "价格已变更，请重新确认";
        }
        return null;
    }

    private BigDecimal toBigDecimalQuietly(Object raw) {
        if (raw instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void serializeAndPut(String key, String field, CartItemDTO item, Long userId) {
        try {
            redisTemplate.opsForHash().put(key, field, objectMapper.writeValueAsString(item));
        } catch (JacksonException e) {
            log.error("序列化购物车项失败, userId={}, skuId={}", userId, field, e);
            throw new BusinessException("CART_SERIALIZE_ERROR", "购物车数据序列化失败");
        }
    }

    private String buildKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    /**
     * Sentinel 降级只应接管流量类异常（BlockException）；业务校验/数据访问异常必须原样上抛，
     * 由全局异常处理器返回错误信封。此前所有异常一律返回 null，被控制器包装成
     * `{"success":true}` 假成功，导致加购静默丢失（明确失败 &gt; 假装成功）。
     */
    public CartDTO getCartFallback(Long userId, Throwable throwable) {
        log.warn("getCart fallback triggered, userId={}: {}", userId, throwable.getMessage());
        throw degradedOrRethrow(throwable);
    }

    public CartItemDTO addToCartFallback(Long userId, AddCartItemRequest request, Throwable throwable) {
        log.warn("addToCart fallback triggered, userId={}, skuId={}: {}",
                userId, request.skuId(), throwable.getMessage());
        throw degradedOrRethrow(throwable);
    }

    private BusinessException degradedOrRethrow(Throwable throwable) {
        if (throwable instanceof com.alibaba.csp.sentinel.slots.block.BlockException) {
            return new BusinessException("CART_SERVICE_UNAVAILABLE", "购物车服务繁忙，请稍后重试");
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return new BusinessException("CART_SERVICE_UNAVAILABLE", "购物车服务暂时不可用，请稍后重试");
    }
}

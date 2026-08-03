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

        List<CartItemDTO> items = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                CartItemDTO item = deserializeItem(entry.getValue().toString(), userId, entry.getKey());
                if (item != null) {
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

        return new CartDTO(items, totalQuantity, totalPrice);
    }

    @Override
    @SentinelResource(value = "addToCart", fallback = "addToCartFallback")
    public CartItemDTO addItem(Long userId, AddCartItemRequest request) {
        String key = buildKey(userId);
        String skuField = request.skuId().toString();

        ProductInfo productInfo = fetchProductInfo(request.productId());

        Object existing = redisTemplate.opsForHash().get(key, skuField);
        CartItemDTO item;

        if (existing != null) {
            try {
                CartItemDTO current = deserializeItem(existing.toString(), userId, skuField);
                item = new CartItemDTO(
                        current.id(), current.userId(), current.productId(), current.skuId(),
                        current.quantity() + request.quantity(), current.checked(),
                        current.productName(), current.skuImage(), current.skuAttributes(), current.price()
                );
            } catch (JacksonException e) {
                log.error("反序列化购物车项失败, userId={}, skuId={}", userId, request.skuId(), e);
                throw new BusinessException("CART_DESERIALIZE_ERROR", "购物车数据解析失败");
            }
        } else {
            String productName = productInfo != null ? productInfo.name() : null;
            String skuImage = null;
            String skuAttributes = null;
            BigDecimal price = null;

            if (productInfo != null && productInfo.skus() != null) {
                SkuInfo matchedSku = productInfo.skus().stream()
                        .filter(s -> s.id().equals(request.skuId()))
                        .findFirst()
                        .orElse(null);
                if (matchedSku != null) {
                    skuImage = matchedSku.image();
                    skuAttributes = matchedSku.attributes();
                    price = matchedSku.price();
                }
            }

            if (skuImage == null && productInfo != null) {
                skuImage = productInfo.mainImage();
            }

            item = new CartItemDTO(
                    null, userId, request.productId(), request.skuId(),
                    request.quantity(), 1, productName, skuImage, skuAttributes, price
            );
        }

        serializeAndPut(key, skuField, item, userId);
        return item;
    }

    @Override
    public CartItemDTO updateItem(Long userId, Long skuId, UpdateCartItemRequest request) {
        String key = buildKey(userId);
        String skuField = skuId.toString();

        Object existing = redisTemplate.opsForHash().get(key, skuField);
        if (existing == null) {
            throw new BusinessException("CART_ITEM_NOT_FOUND", "购物车项不存在");
        }

        try {
            CartItemDTO current = deserializeItem(existing.toString(), userId, skuField);
            CartItemDTO updated = new CartItemDTO(
                    current.id(), current.userId(), current.productId(), current.skuId(),
                    request.quantity() != null ? request.quantity() : current.quantity(),
                    request.checked() != null ? request.checked() : current.checked(),
                    current.productName(), current.skuImage(), current.skuAttributes(), current.price()
            );
            serializeAndPut(key, skuField, updated, userId);
            return updated;
        } catch (JacksonException e) {
            log.error("反序列化购物车项失败, userId={}, skuId={}", userId, skuId, e);
            throw new BusinessException("CART_DESERIALIZE_ERROR", "购物车数据解析失败");
        }
    }

    @Override
    public void removeItem(Long userId, Long skuId) {
        String key = buildKey(userId);
        String skuField = skuId.toString();

        Object existing = redisTemplate.opsForHash().get(key, skuField);
        if (existing == null) {
            throw new BusinessException("CART_ITEM_NOT_FOUND", "购物车项不存在");
        }

        redisTemplate.opsForHash().delete(key, skuField);
    }

    @Override
    public void clearCart(Long userId) {
        redisTemplate.delete(buildKey(userId));
    }

    @Override
    public void clearCheckedItems(Long userId) {
        String key = buildKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                CartItemDTO item = deserializeItem(entry.getValue().toString(), userId, entry.getKey());
                if (item != null && item.checked() != null && item.checked() == 1) {
                    redisTemplate.opsForHash().delete(key, entry.getKey().toString());
                }
            } catch (JacksonException e) {
                log.error("反序列化购物车项失败, userId={}, skuId={}", userId, entry.getKey(), e);
            }
        }
    }

    @Override
    public void syncToDatabase(Long userId) {
        String key = buildKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
            return;
        }

        List<CartItem> redisItems = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                CartItemDTO dto = deserializeItem(entry.getValue().toString(), userId, entry.getKey());
                if (dto != null) {
                    CartItem entity = new CartItem();
                    entity.setUserId(dto.userId());
                    entity.setProductId(dto.productId());
                    entity.setSkuId(dto.skuId());
                    entity.setQuantity(dto.quantity());
                    entity.setChecked(dto.checked());
                    redisItems.add(entity);
                }
            } catch (JacksonException e) {
                log.error("同步购物车反序列化失败, userId={}, skuId={}", userId, entry.getKey(), e);
            }
        }

        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));

        for (CartItem item : redisItems) {
            cartItemMapper.insert(item);
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

    public CartDTO getCartFallback(Long userId, Throwable throwable) {
        log.warn("getCart fallback triggered, userId={}: {}", userId, throwable.getMessage());
        return null;
    }

    public CartItemDTO addToCartFallback(Long userId, AddCartItemRequest request, Throwable throwable) {
        log.warn("addToCart fallback triggered, userId={}: {}", userId, throwable.getMessage());
        return null;
    }
}

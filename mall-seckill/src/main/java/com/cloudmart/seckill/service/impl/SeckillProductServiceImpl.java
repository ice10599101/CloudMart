package com.cloudmart.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.service.SeckillProductService;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SeckillProductServiceImpl implements SeckillProductService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final SeckillProductMapper productMapper;
    private final SeckillActivityMapper activityMapper;
    private final SeckillConverter seckillConverter;
    private final com.cloudmart.seckill.feign.ProductFeignClient productFeignClient;
    private final StringRedisTemplate redisTemplate;

    public SeckillProductServiceImpl(SeckillProductMapper productMapper,
                                     SeckillActivityMapper activityMapper,
                                     SeckillConverter seckillConverter,
                                     com.cloudmart.seckill.feign.ProductFeignClient productFeignClient,
                                     StringRedisTemplate redisTemplate) {
        this.productMapper = productMapper;
        this.activityMapper = activityMapper;
        this.seckillConverter = seckillConverter;
        this.productFeignClient = productFeignClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public SeckillProductDTO addProduct(Long activityId, AddSeckillProductRequest request) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        if (request.seckillPrice().compareTo(request.originalPrice()) >= 0) {
            throw new BusinessException("INVALID_PRICE", "秒杀价格必须低于原价");
        }

        Long existingCount = productMapper.selectCount(
                new LambdaQueryWrapper<SeckillProduct>()
                        .eq(SeckillProduct::getActivityId, activityId)
                        .eq(SeckillProduct::getSkuId, request.skuId())
        );
        if (existingCount > 0) {
            throw new BusinessException("PRODUCT_ALREADY_EXISTS", "该商品已在此活动中");
        }

        SeckillProduct entity = seckillConverter.toEntity(request);
        entity.setActivityId(activityId);
        entity.setAvailableStock(request.totalStock());
        entity.setStatus("ON_SHELF");
        productMapper.insert(entity);

        loadStockToRedis(activityId, entity.getId());

        return seckillConverter.toProductDTO(entity);
    }

    @Override
    public SeckillProductDTO getProduct(Long productId) {
        SeckillProduct entity = productMapper.selectById(productId);
        if (entity == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "秒杀商品不存在");
        }
        return seckillConverter.toProductDTO(entity);
    }

    @Override
    public List<SeckillProductDTO> listProductsByActivity(Long activityId) {
        List<SeckillProduct> products = productMapper.selectList(
                new LambdaQueryWrapper<SeckillProduct>()
                        .eq(SeckillProduct::getActivityId, activityId)
                        .eq(SeckillProduct::getStatus, "ON_SHELF")
        );
        List<SeckillProductDTO> dtos = seckillConverter.toProductDTOList(products);
        enrichProductInfo(dtos);
        return dtos;
    }

    /**
     * 用 mall-product 批量查询补齐商品名称/图片/商品ID；
     * enrich 失败仅记日志，列表仍按库存数据返回（名称为空由前端兜底展示）。
     */
    private void enrichProductInfo(List<SeckillProductDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<Long> skuIds = dtos.stream()
                .map(SeckillProductDTO::skuId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (skuIds.isEmpty()) {
            return;
        }
        try {
            var response = productFeignClient.getSkusBatch(skuIds);
            if (response == null || response.data() == null) {
                return;
            }
            Map<Long, com.cloudmart.seckill.feign.ProductFeignClient.SkuBatchItem> infoMap = response.data().stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            com.cloudmart.seckill.feign.ProductFeignClient.SkuBatchItem::skuId, item -> item,
                            (a, b) -> a));
            for (SeckillProductDTO dto : dtos) {
                var info = infoMap.get(dto.skuId());
                if (info != null) {
                    // 通过 MapStruct 生成的 record 访问器无法就地修改，使用反射不可取，
                    // 这里直接以新的 DTO 实例替换列表元素
                    int idx = dtos.indexOf(dto);
                    dtos.set(idx, new SeckillProductDTO(
                            dto.id(), dto.activityId(), dto.skuId(),
                            info.productId(), info.productName(), info.image(),
                            dto.seckillPrice(), dto.originalPrice(),
                            dto.totalStock(), dto.availableStock(), dto.perUserLimit(),
                            dto.status(), dto.createdAt()));
                }
            }
        } catch (Exception ex) {
            log.warn("秒杀商品信息 enrich 失败: {}", ex.getMessage());
        }
    }

    @Override
    public void loadStockToRedis(Long activityId, Long productId) {
        SeckillProduct product = productMapper.selectById(productId);
        if (product != null) {
            String key = STOCK_KEY_PREFIX + activityId + ":" + productId;
            redisTemplate.opsForValue().set(key, String.valueOf(product.getAvailableStock()));
        }
    }

    @Override
    public void loadAllStocksToRedis() {
        List<SeckillProduct> products = productMapper.selectList(
                new LambdaQueryWrapper<SeckillProduct>()
                        .eq(SeckillProduct::getStatus, "ON_SHELF")
        );
        for (SeckillProduct product : products) {
            String key = STOCK_KEY_PREFIX + product.getActivityId() + ":" + product.getId();
            redisTemplate.opsForValue().set(key, String.valueOf(product.getAvailableStock()));
        }
    }
}

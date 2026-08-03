package com.cloudmart.seckill.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeckillProductServiceImpl 单元测试")
class SeckillProductServiceImplTest {

    @Mock
    private SeckillProductMapper productMapper;

    @Mock
    private SeckillActivityMapper activityMapper;

    @Mock
    private SeckillConverter seckillConverter;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeckillProductServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SeckillProduct.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SeckillActivity.class);
    }

    @SuppressWarnings("unchecked")
    private void stubRedisOpsForValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private SeckillActivity buildActivity(Long id) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setName("秒杀活动");
        activity.setStatus("UPCOMING");
        activity.setStartTime(NOW.plusDays(1));
        activity.setEndTime(NOW.plusDays(2));
        return activity;
    }

    private SeckillProduct buildProduct(Long id, Long activityId, Integer stock) {
        SeckillProduct product = new SeckillProduct();
        product.setId(id);
        product.setActivityId(activityId);
        product.setSkuId(100L);
        product.setSeckillPrice(new BigDecimal("9.99"));
        product.setOriginalPrice(new BigDecimal("19.99"));
        product.setTotalStock(stock);
        product.setAvailableStock(stock);
        product.setPerUserLimit(1);
        product.setStatus("ON_SHELF");
        product.setCreatedAt(NOW);
        return product;
    }

    private SeckillProductDTO buildProductDTO(SeckillProduct product) {
        return new SeckillProductDTO(
                product.getId(), product.getActivityId(), product.getSkuId(),
                product.getSeckillPrice(), product.getOriginalPrice(),
                product.getTotalStock(), product.getAvailableStock(),
                product.getPerUserLimit(), product.getStatus(), product.getCreatedAt()
        );
    }

    @Nested
    @DisplayName("addProduct 方法")
    class AddProductTest {

        @Test
        @DisplayName("正常添加秒杀商品 - 成功")
        void shouldAddProductSuccessfully() {
            stubRedisOpsForValue();
            Long activityId = 1L;
            AddSeckillProductRequest request = new AddSeckillProductRequest(
                    100L, new BigDecimal("9.99"), new BigDecimal("19.99"), 50, 1
            );
            SeckillActivity activity = buildActivity(activityId);
            SeckillProduct entity = buildProduct(null, activityId, 50);
            SeckillProduct savedEntity = buildProduct(1L, activityId, 50);
            SeckillProductDTO dto = buildProductDTO(savedEntity);

            when(activityMapper.selectById(activityId)).thenReturn(activity);
            when(productMapper.selectCount(any())).thenReturn(0L);
            when(seckillConverter.toEntity(request)).thenReturn(entity);
            when(productMapper.selectById(any())).thenReturn(savedEntity);
            when(seckillConverter.toProductDTO(entity)).thenReturn(dto);

            SeckillProductDTO result = service.addProduct(activityId, request);

            assertThat(result).isNotNull();
            assertThat(entity.getActivityId()).isEqualTo(activityId);
            assertThat(entity.getAvailableStock()).isEqualTo(50);
            assertThat(entity.getStatus()).isEqualTo("ON_SHELF");
            verify(productMapper).insert(any(SeckillProduct.class));
        }

        @Test
        @DisplayName("活动不存在 - 抛出异常")
        void shouldThrowWhenActivityNotFound() {
            when(activityMapper.selectById(anyLong())).thenReturn(null);

            AddSeckillProductRequest request = new AddSeckillProductRequest(
                    100L, new BigDecimal("9.99"), new BigDecimal("19.99"), 50, 1
            );

            assertThatThrownBy(() -> service.addProduct(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACTIVITY_NOT_FOUND"));
        }

        @Test
        @DisplayName("秒杀价格不低于原价 - 抛出异常")
        void shouldThrowWhenSeckillPriceNotLowerThanOriginal() {
            SeckillActivity activity = buildActivity(1L);
            when(activityMapper.selectById(1L)).thenReturn(activity);

            AddSeckillProductRequest request = new AddSeckillProductRequest(
                    100L, new BigDecimal("19.99"), new BigDecimal("19.99"), 50, 1
            );

            assertThatThrownBy(() -> service.addProduct(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_PRICE"));
        }

        @Test
        @DisplayName("商品已存在于活动中 - 抛出异常")
        void shouldThrowWhenProductAlreadyExists() {
            SeckillActivity activity = buildActivity(1L);
            when(activityMapper.selectById(1L)).thenReturn(activity);
            when(productMapper.selectCount(any())).thenReturn(1L);

            AddSeckillProductRequest request = new AddSeckillProductRequest(
                    100L, new BigDecimal("9.99"), new BigDecimal("19.99"), 50, 1
            );

            assertThatThrownBy(() -> service.addProduct(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PRODUCT_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("getProduct 方法")
    class GetProductTest {

        @Test
        @DisplayName("商品存在 - 返回DTO")
        void shouldReturnProductWhenExists() {
            SeckillProduct product = buildProduct(1L, 10L, 50);
            SeckillProductDTO dto = buildProductDTO(product);

            when(productMapper.selectById(1L)).thenReturn(product);
            when(seckillConverter.toProductDTO(product)).thenReturn(dto);

            SeckillProductDTO result = service.getProduct(1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("商品不存在 - 抛出异常")
        void shouldThrowWhenProductNotFound() {
            when(productMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.getProduct(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("listProductsByActivity 方法")
    class ListProductsByActivityTest {

        @Test
        @DisplayName("按活动ID查询上架商品 - 成功")
        void shouldListOnShelfProducts() {
            SeckillProduct p1 = buildProduct(1L, 10L, 50);
            SeckillProduct p2 = buildProduct(2L, 10L, 30);
            SeckillProductDTO dto1 = buildProductDTO(p1);
            SeckillProductDTO dto2 = buildProductDTO(p2);

            when(productMapper.selectList(any())).thenReturn(List.of(p1, p2));
            when(seckillConverter.toProductDTOList(List.of(p1, p2))).thenReturn(List.of(dto1, dto2));

            List<SeckillProductDTO> result = service.listProductsByActivity(10L);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("loadStockToRedis 方法")
    class LoadStockToRedisTest {

        @Test
        @DisplayName("商品存在 - 将库存加载到Redis")
        void shouldLoadStockToRedis() {
            stubRedisOpsForValue();
            SeckillProduct product = buildProduct(1L, 10L, 50);

            when(productMapper.selectById(1L)).thenReturn(product);

            service.loadStockToRedis(10L, 1L);

            verify(valueOperations).set(eq("seckill:stock:10:1"), eq("50"));
        }

        @Test
        @DisplayName("商品不存在 - 不操作Redis")
        void shouldNotLoadWhenProductNull() {
            when(productMapper.selectById(anyLong())).thenReturn(null);

            service.loadStockToRedis(10L, 999L);

            verify(valueOperations, never()).set(any(), any());
        }
    }

    @Nested
    @DisplayName("loadAllStocksToRedis 方法")
    class LoadAllStocksToRedisTest {

        @Test
        @DisplayName("加载所有上架商品库存到Redis")
        void shouldLoadAllStocksToRedis() {
            stubRedisOpsForValue();
            SeckillProduct p1 = buildProduct(1L, 10L, 50);
            SeckillProduct p2 = buildProduct(2L, 20L, 30);

            when(productMapper.selectList(any())).thenReturn(List.of(p1, p2));

            service.loadAllStocksToRedis();

            verify(valueOperations).set(eq("seckill:stock:10:1"), eq("50"));
            verify(valueOperations).set(eq("seckill:stock:20:2"), eq("30"));
        }
    }
}

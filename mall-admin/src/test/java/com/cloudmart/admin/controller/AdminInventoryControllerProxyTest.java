package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.InventoryAdjustRequest;
import com.cloudmart.admin.dto.InventoryInitRequest;
import com.cloudmart.admin.dto.feign.InventoryAdjustmentDTO;
import com.cloudmart.admin.dto.feign.InventoryDTO;
import com.cloudmart.admin.dto.feign.InventoryRowDTO;
import com.cloudmart.admin.dto.feign.ProductDTO;
import com.cloudmart.admin.dto.feign.SkuBatchDTO;
import com.cloudmart.admin.dto.feign.SkuDTO;
import com.cloudmart.admin.feign.InventoryFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.admin.feign.ProductSkuQueryFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TRADE-02：库存代理契约——初始化归属校验、调整单操作者转发、列表批量补名。
 */
@ExtendWith(MockitoExtension.class)
class AdminInventoryControllerProxyTest {

    @Mock
    private InventoryFeignClient inventoryFeignClient;
    @Mock
    private ProductFeignClient productFeignClient;
    @Mock
    private ProductSkuQueryFeignClient productSkuQueryFeignClient;

    private AdminInventoryController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminInventoryController(inventoryFeignClient, productFeignClient,
                productSkuQueryFeignClient);
    }

    private ProductDTO productWithSkus(Long productId, List<Long> skuIds) {
        List<SkuDTO> skus = skuIds.stream()
                .map(id -> new SkuDTO(id, productId, "SKU-" + id, "红色", null, null, 0, null, 1))
                .toList();
        return new ProductDTO(productId, "测试商品", null, null, null, null, null, 1, skus, null);
    }

    private InventoryDTO inventoryRow(Long skuId) {
        return new InventoryDTO(1L, 7L, skuId, 100, 5, 3, null);
    }

    @Test
    @DisplayName("初始化：SKU 属于该商品 → 放行转发（skuId/productId/stock 原样）")
    void init_validAffiliation_forwards() {
        when(productFeignClient.getProductById(7L)).thenReturn(ApiResponse.ok(productWithSkus(7L, List.of(11L))));
        when(inventoryFeignClient.initStock(11L, 7L, 50)).thenReturn(ApiResponse.ok(null));

        ApiResponse<Void> response = controller.initInventory(new InventoryInitRequest(11L, 7L, 50), 9001L);

        assertThat(response.success()).isTrue();
        verify(inventoryFeignClient).initStock(11L, 7L, 50);
    }

    @Test
    @DisplayName("初始化：SKU 不属于该商品 → 拒绝（INVENTORY_SKU_MISMATCH），不触达库存服务")
    void init_wrongAffiliation_rejected() {
        when(productFeignClient.getProductById(7L)).thenReturn(ApiResponse.ok(productWithSkus(7L, List.of(11L))));

        assertThatThrownBy(() -> controller.initInventory(new InventoryInitRequest(22L, 7L, 50), 9001L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_SKU_MISMATCH");
        verify(inventoryFeignClient, never()).initStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("初始化：商品不存在 → 拒绝（PRODUCT_NOT_FOUND）")
    void init_productMissing_rejected() {
        when(productFeignClient.getProductById(7L)).thenReturn(ApiResponse.fail("PRODUCT_NOT_FOUND", "商品不存在"));

        assertThatThrownBy(() -> controller.initInventory(new InventoryInitRequest(11L, 7L, 50), 9001L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("调整单：操作者取认证上下文转发（不信任 body），请求体原样")
    void adjust_forwardsOperatorFromContext() {
        InventoryAdjustRequest request =
                new InventoryAdjustRequest("11", -3, "月度盘点盘亏", 3, "req-key-1");
        when(inventoryFeignClient.adjustStock(request, 9001L))
                .thenReturn(ApiResponse.ok(new InventoryAdjustmentDTO(
                        "ADJ2026101700001", 11L, 7L, -3, "月度盘点盘亏", "req-key-1",
                        3, 100, 97, 5, 9001L, null)));

        ApiResponse<InventoryAdjustmentDTO> response = controller.adjustInventory(request, 9001L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().afterAvailable()).isEqualTo(97);
        verify(inventoryFeignClient).adjustStock(request, 9001L);
    }

    @Test
    @DisplayName("列表：批量补齐商品名/SKU 规格（单次 /skus/batch）")
    void list_enrichesWithProductInfo() {
        when(inventoryFeignClient.listInventory(null, 1, 20)).thenReturn(ApiResponse.ok(
                List.of(inventoryRow(11L), inventoryRow(22L)),
                new ApiResponse.Meta(1, 20, 2L)));
        when(productSkuQueryFeignClient.getSkusBatch(List.of(11L, 22L))).thenReturn(ApiResponse.ok(List.of(
                new SkuBatchDTO(11L, 7L, "测试商品", null, null, 1, "红色"),
                new SkuBatchDTO(22L, 8L, "另一商品", null, null, 1, "蓝色"))));

        ApiResponse<List<InventoryRowDTO>> response = controller.listInventory(null, 1, 20);

        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).productName()).isEqualTo("测试商品");
        assertThat(response.data().get(0).skuSpec()).isEqualTo("红色");
        assertThat(response.data().get(0).availableStock()).isEqualTo(100);
        assertThat(response.data().get(1).productName()).isEqualTo("另一商品");
        assertThat(response.data().get(0).productInfoStatus()).isEqualTo("OK");
        assertThat(response.meta()).isNotNull();
    }

    @Test
    @DisplayName("T12：SKU 已删除/下架时保留库存事实并标记 MISSING")
    void list_missingSku_marksMissing() {
        when(inventoryFeignClient.listInventory(null, 1, 20))
                .thenReturn(ApiResponse.ok(List.of(inventoryRow(11L), inventoryRow(99L))));
        when(productSkuQueryFeignClient.getSkusBatch(List.of(11L, 99L))).thenReturn(ApiResponse.ok(List.of(
                new SkuBatchDTO(11L, 7L, "测试商品", null, null, 1, "红色"))));

        ApiResponse<List<InventoryRowDTO>> response = controller.listInventory(null, 1, 20);

        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(1).productInfoStatus()).isEqualTo("MISSING");
        assertThat(response.data().get(1).productName()).isNull();
        assertThat(response.data().get(1).availableStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("列表：补名失败降级为空名，库存事实照常返回")
    void list_enrichFailure_degradesGracefully() {
        when(inventoryFeignClient.listInventory(null, 1, 20))
                .thenReturn(ApiResponse.ok(List.of(inventoryRow(11L))));
        when(productSkuQueryFeignClient.getSkusBatch(any())).thenThrow(
                new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用"));

        ApiResponse<List<InventoryRowDTO>> response = controller.listInventory(null, 1, 20);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).productName()).isNull();
        // T12：降级必须显式标记，前端据此显示“商品信息暂不可用”，不空白成功
        assertThat(response.data().get(0).productInfoStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.data().get(0).availableStock()).isEqualTo(100);
    }
}

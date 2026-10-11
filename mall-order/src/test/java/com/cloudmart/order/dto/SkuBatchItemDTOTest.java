package com.cloudmart.order.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R24/AT34：雪花 ID 以 JSON 字符串形态（JsSafeLongSerializer）传输时，
 * 强类型 DTO 的反序列化必须还原精确 Long 值——Map + instanceof Number 形态下
 * 这些行会被静默判为"SKU 不存在"。
 */
class SkuBatchItemDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("超过 JS 安全范围的字符串 ID → 精确 Long（不截断不丢失）")
    void deserialize_stringSnowflakeId_exactLong() throws Exception {
        String json = "{\"skuId\":\"9007199254740993\",\"productId\":\"300\","
                + "\"productName\":\"商品A\",\"image\":\"img.jpg\",\"price\":\"99.00\","
                + "\"status\":1,\"attributes\":\"红色\"}";

        SkuBatchItemDTO dto = objectMapper.readValue(json, SkuBatchItemDTO.class);

        assertThat(dto.skuId()).isEqualTo(9007199254740993L);
        assertThat(dto.productId()).isEqualTo(300L);
        assertThat(dto.price()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(dto.status()).isEqualTo(1);
    }

    @Test
    @DisplayName("正常小 ID 的数字形态 → Long（兼容未启用 JsSafeLong 的响应）")
    void deserialize_numberId_exactLong() throws Exception {
        String json = "{\"skuId\":100,\"productId\":200,\"price\":10.50,\"status\":1}";

        SkuBatchItemDTO dto = objectMapper.readValue(json, SkuBatchItemDTO.class);

        assertThat(dto.skuId()).isEqualTo(100L);
        assertThat(dto.productId()).isEqualTo(200L);
    }
}

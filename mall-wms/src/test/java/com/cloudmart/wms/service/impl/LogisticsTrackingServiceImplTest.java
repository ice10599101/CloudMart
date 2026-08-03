package com.cloudmart.wms.service.impl;

import com.cloudmart.wms.dto.ShippingTrackingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogisticsTrackingServiceImpl 单元测试")
class LogisticsTrackingServiceImplTest {

    private LogisticsTrackingServiceImpl logisticsTrackingService;

    @BeforeEach
    void setUp() {
        logisticsTrackingService = new LogisticsTrackingServiceImpl();
    }

    @Nested
    @DisplayName("queryTracking 方法")
    class QueryTrackingTests {

        @Test
        @DisplayName("查询物流轨迹 - 返回非空列表")
        void shouldReturnNonEmptyList() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("查询物流轨迹 - 返回5条模拟轨迹")
        void shouldReturnFiveTrackingRecords() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("查询物流轨迹 - 第一条为签收记录")
        void shouldFirstRecordBeSigned() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result.getFirst().description()).contains("签收");
        }

        @Test
        @DisplayName("查询物流轨迹 - 最后一条为揽收记录")
        void shouldLastRecordBeCollected() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result.getLast().description()).contains("揽收");
        }

        @Test
        @DisplayName("查询物流轨迹 - 每条记录都有location")
        void shouldAllRecordsHaveLocation() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result).allSatisfy(dto ->
                assertThat(dto.location()).isNotBlank()
            );
        }

        @Test
        @DisplayName("查询物流轨迹 - 每条记录都有description")
        void shouldAllRecordsHaveDescription() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result).allSatisfy(dto ->
                assertThat(dto.description()).isNotBlank()
            );
        }

        @Test
        @DisplayName("查询物流轨迹 - 每条记录都有happenedAt时间")
        void shouldAllRecordsHaveHappenedAt() {
            List<ShippingTrackingDTO> result = logisticsTrackingService.queryTracking("SF1234567890", "顺丰");

            assertThat(result).allSatisfy(dto ->
                assertThat(dto.happenedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("查询物流轨迹 - 不同运单号返回相同模拟数据")
        void shouldReturnSameDataForDifferentShippingNo() {
            List<ShippingTrackingDTO> result1 = logisticsTrackingService.queryTracking("SF111", "顺丰");
            List<ShippingTrackingDTO> result2 = logisticsTrackingService.queryTracking("YT222", "圆通");

            assertThat(result1).hasSize(result2.size());
        }
    }
}

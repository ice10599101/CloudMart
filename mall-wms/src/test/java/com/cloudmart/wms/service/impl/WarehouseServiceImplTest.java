package com.cloudmart.wms.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateWarehouseRequest;
import com.cloudmart.wms.dto.UpdateWarehouseRequest;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.repository.WarehouseMapper;
import com.cloudmart.wms.vo.WarehouseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceImplTest {

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private WmsConverter wmsConverter;

    private WarehouseServiceImpl warehouseService;

    private static final Long WAREHOUSE_ID = 1L;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseServiceImpl(warehouseMapper, wmsConverter);
    }

    @Nested
    @DisplayName("createWarehouse")
    class CreateWarehouseTests {

        @Test
        @DisplayName("should create warehouse and return VO")
        void createWarehouse_success_returnsVO() {
            CreateWarehouseRequest request = new CreateWarehouseRequest("主仓", "北京市朝阳区", "010-12345678", 0);
            WarehouseVO expectedVO = new WarehouseVO(WAREHOUSE_ID, "主仓", null, "北京市朝阳区", "010-12345678", 0);

            when(warehouseMapper.insert(any(Warehouse.class))).thenReturn(1);
            when(wmsConverter.toWarehouseVO(any(Warehouse.class))).thenReturn(expectedVO);

            WarehouseVO result = warehouseService.createWarehouse(request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("主仓");
            assertThat(result.address()).isEqualTo("北京市朝阳区");
            verify(warehouseMapper).insert(any(Warehouse.class));
        }

        @Test
        @DisplayName("should default status to 0 when null")
        void createWarehouse_nullStatus_defaultsToZero() {
            CreateWarehouseRequest request = new CreateWarehouseRequest("主仓", "北京市朝阳区", "010-12345678", null);
            WarehouseVO expectedVO = new WarehouseVO(WAREHOUSE_ID, "主仓", null, "北京市朝阳区", "010-12345678", 0);

            when(warehouseMapper.insert(any(Warehouse.class))).thenReturn(1);
            when(wmsConverter.toWarehouseVO(any(Warehouse.class))).thenReturn(expectedVO);

            warehouseService.createWarehouse(request);

            verify(warehouseMapper).insert(any(Warehouse.class));
        }
    }

    @Nested
    @DisplayName("getWarehouse")
    class GetWarehouseTests {

        @Test
        @DisplayName("should return warehouse VO when found")
        void getWarehouse_existing_returnsVO() {
            Warehouse warehouse = new Warehouse();
            warehouse.setId(WAREHOUSE_ID);
            warehouse.setName("主仓");

            WarehouseVO expectedVO = new WarehouseVO(WAREHOUSE_ID, "主仓", null, "北京市朝阳区", "010-12345678", 0);

            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
            when(wmsConverter.toWarehouseVO(warehouse)).thenReturn(expectedVO);

            WarehouseVO result = warehouseService.getWarehouse(WAREHOUSE_ID);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(WAREHOUSE_ID);
        }

        @Test
        @DisplayName("should throw when warehouse not found")
        void getWarehouse_nonExistent_throwsException() {
            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(null);

            assertThatThrownBy(() -> warehouseService.getWarehouse(WAREHOUSE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("WAREHOUSE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("listWarehouses")
    class ListWarehousesTests {

        @Test
        @DisplayName("should return list of warehouse VOs")
        void listWarehouses_returnsVOList() {
            Warehouse warehouse1 = new Warehouse();
            warehouse1.setId(1L);
            warehouse1.setName("主仓");
            Warehouse warehouse2 = new Warehouse();
            warehouse2.setId(2L);
            warehouse2.setName("分仓");

            WarehouseVO vo1 = new WarehouseVO(1L, "主仓", null, "北京", "010-11111111", 0);
            WarehouseVO vo2 = new WarehouseVO(2L, "分仓", null, "上海", "021-22222222", 0);

            when(warehouseMapper.selectList(null)).thenReturn(List.of(warehouse1, warehouse2));
            when(wmsConverter.toWarehouseVOList(List.of(warehouse1, warehouse2)))
                    .thenReturn(List.of(vo1, vo2));

            List<WarehouseVO> result = warehouseService.listWarehouses();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("主仓");
            assertThat(result.get(1).name()).isEqualTo("分仓");
        }
    }

    @Nested
    @DisplayName("updateWarehouse")
    class UpdateWarehouseTests {

        @Test
        @DisplayName("should update warehouse and return VO")
        void updateWarehouse_existing_updatesAndReturnsVO() {
            UpdateWarehouseRequest request = new UpdateWarehouseRequest("新名称", "新地址", "新电话", 1);
            Warehouse existing = new Warehouse();
            existing.setId(WAREHOUSE_ID);
            existing.setName("旧名称");
            existing.setAddress("旧地址");
            existing.setContactPhone("旧电话");
            existing.setStatus(0);

            WarehouseVO expectedVO = new WarehouseVO(WAREHOUSE_ID, "新名称", null, "新地址", "新电话", 1);

            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(existing);
            when(warehouseMapper.updateById(existing)).thenReturn(1);
            when(wmsConverter.toWarehouseVO(existing)).thenReturn(expectedVO);

            WarehouseVO result = warehouseService.updateWarehouse(WAREHOUSE_ID, request);

            assertThat(result).isNotNull();
            assertThat(existing.getName()).isEqualTo("新名称");
            assertThat(existing.getAddress()).isEqualTo("新地址");
            assertThat(existing.getContactPhone()).isEqualTo("新电话");
            assertThat(existing.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("should only update non-null fields")
        void updateWarehouse_partialUpdate_onlyUpdatesNonNullFields() {
            UpdateWarehouseRequest request = new UpdateWarehouseRequest("新名称", null, null, null);
            Warehouse existing = new Warehouse();
            existing.setId(WAREHOUSE_ID);
            existing.setName("旧名称");
            existing.setAddress("旧地址");
            existing.setContactPhone("旧电话");
            existing.setStatus(0);

            WarehouseVO expectedVO = new WarehouseVO(WAREHOUSE_ID, "新名称", null, "旧地址", "旧电话", 0);

            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(existing);
            when(warehouseMapper.updateById(existing)).thenReturn(1);
            when(wmsConverter.toWarehouseVO(existing)).thenReturn(expectedVO);

            WarehouseVO result = warehouseService.updateWarehouse(WAREHOUSE_ID, request);

            assertThat(existing.getName()).isEqualTo("新名称");
            assertThat(existing.getAddress()).isEqualTo("旧地址");
            assertThat(existing.getStatus()).isEqualTo(0);
        }

        @Test
        @DisplayName("should throw when warehouse not found")
        void updateWarehouse_nonExistent_throwsException() {
            UpdateWarehouseRequest request = new UpdateWarehouseRequest("新名称", null, null, null);

            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(null);

            assertThatThrownBy(() -> warehouseService.updateWarehouse(WAREHOUSE_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("WAREHOUSE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("deleteWarehouse")
    class DeleteWarehouseTests {

        @Test
        @DisplayName("should delete warehouse when found")
        void deleteWarehouse_existing_deletesWarehouse() {
            Warehouse existing = new Warehouse();
            existing.setId(WAREHOUSE_ID);

            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(existing);
            when(warehouseMapper.deleteById(WAREHOUSE_ID)).thenReturn(1);

            warehouseService.deleteWarehouse(WAREHOUSE_ID);

            verify(warehouseMapper).deleteById(WAREHOUSE_ID);
        }

        @Test
        @DisplayName("should throw when warehouse not found")
        void deleteWarehouse_nonExistent_throwsException() {
            when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(null);

            assertThatThrownBy(() -> warehouseService.deleteWarehouse(WAREHOUSE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("WAREHOUSE_NOT_FOUND");
        }
    }
}

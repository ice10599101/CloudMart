package com.cloudmart.inventory.converter;

import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.dto.InventoryLogDTO;
import com.cloudmart.inventory.entity.Inventory;
import com.cloudmart.inventory.entity.InventoryLog;
import com.cloudmart.inventory.vo.InventoryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryConverter {

    InventoryDTO toDTO(Inventory inventory);

    InventoryLogDTO toLogDTO(InventoryLog log);

    @Mapping(target = "availableStock", source = "available")
    @Mapping(target = "lockedStock", source = "reserved")
    InventoryVO toVO(Inventory inventory);

    @Mapping(target = "availableStock", source = "available")
    @Mapping(target = "lockedStock", source = "reserved")
    @Mapping(target = "updatedAt", ignore = true)
    InventoryVO dtoToVO(InventoryDTO dto);
}

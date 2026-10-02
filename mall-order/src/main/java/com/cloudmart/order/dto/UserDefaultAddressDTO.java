package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户默认收货地址（T09/T10）：mall-user 只读视图，系统建单填充收货人三字段。
 */
@Schema(description = "用户默认收货地址")
public record UserDefaultAddressDTO(
    Long id,
    String receiverName,
    String phone,
    String province,
    String city,
    String district,
    String detailAddress,
    Boolean isDefault
) {

    /** 拼接完整地址（省市区 + 详细地址） */
    public String fullAddress() {
        return (province == null ? "" : province)
                + (city == null ? "" : city)
                + (district == null ? "" : district)
                + (detailAddress == null ? "" : detailAddress);
    }
}

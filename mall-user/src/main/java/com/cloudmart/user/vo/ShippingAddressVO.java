package com.cloudmart.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "收货地址VO")
public record ShippingAddressVO(
    @Schema(description = "地址ID") Long id,
    @Schema(description = "收件人姓名") String receiverName,
    @Schema(description = "收件人电话") String phone,
    @Schema(description = "省份") String province,
    @Schema(description = "城市") String city,
    @Schema(description = "区县") String district,
    @Schema(description = "详细地址") String detailAddress,
    @Schema(description = "是否默认") Boolean isDefault
) {}

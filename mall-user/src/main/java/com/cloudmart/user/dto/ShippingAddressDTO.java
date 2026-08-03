package com.cloudmart.user.dto;

import java.time.LocalDateTime;

public record ShippingAddressDTO(
    Long id,
    Long userId,
    String receiverName,
    String receiverPhone,
    String province,
    String city,
    String district,
    String detailAddress,
    Boolean isDefault,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

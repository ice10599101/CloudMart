package com.cloudmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
    @NotBlank @Size(max = 64) String receiverName,
    @NotBlank @Size(max = 20) String receiverPhone,
    @NotBlank @Size(max = 32) String province,
    @NotBlank @Size(max = 32) String city,
    @NotBlank @Size(max = 32) String district,
    @NotBlank @Size(max = 256) String detailAddress,
    Boolean isDefault
) {}

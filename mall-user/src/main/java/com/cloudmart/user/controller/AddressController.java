package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.user.dto.CreateAddressRequest;
import com.cloudmart.user.dto.UpdateAddressRequest;
import com.cloudmart.user.service.AddressService;
import com.cloudmart.user.vo.ShippingAddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/addresses")
@Tag(name = "收货地址管理", description = "收货地址增删改查接口")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    @Operation(summary = "新增收货地址", description = "添加收货地址，第一个地址自动设为默认，上限20个")
    public ApiResponse<ShippingAddressVO> createAddress(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "新增收货地址请求体") @Valid @RequestBody CreateAddressRequest request) {
        return ApiResponse.ok(addressService.createAddress(userId, request));
    }

    @GetMapping
    @Operation(summary = "地址列表", description = "查询当前用户的所有收货地址，默认地址排在最前")
    public ApiResponse<List<ShippingAddressVO>> listAddresses(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(addressService.listAddresses(userId));
    }

    @GetMapping("/default")
    @Operation(summary = "默认地址", description = "查询当前用户的默认收货地址")
    public ApiResponse<ShippingAddressVO> getDefaultAddress(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(addressService.getDefaultAddress(userId));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "更新地址", description = "更新收货地址信息")
    public ApiResponse<ShippingAddressVO> updateAddress(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "地址ID", required = true) @PathVariable("addressId") Long addressId,
            @Parameter(description = "更新地址请求体") @Valid @RequestBody UpdateAddressRequest request) {
        return ApiResponse.ok(addressService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "删除地址", description = "删除收货地址，若删除的是默认地址则自动将最早的一个设为默认")
    public ApiResponse<Void> deleteAddress(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "地址ID", required = true) @PathVariable("addressId") Long addressId) {
        addressService.deleteAddress(userId, addressId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{addressId}/default")
    @Operation(summary = "设为默认地址", description = "将指定地址设为默认收货地址")
    public ApiResponse<ShippingAddressVO> setDefaultAddress(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "地址ID", required = true) @PathVariable("addressId") Long addressId) {
        return ApiResponse.ok(addressService.setDefaultAddress(userId, addressId));
    }
}

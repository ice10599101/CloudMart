package com.cloudmart.marketing.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * mall-user 内部地址查询客户端（T11）：参团时解析收货快照——成团建单使用
 * 参团时的地址，不被异步建单时的新默认地址替换。
 */
@FeignClient(contextId = "marketingUserAddressFeignClient", name = "mall-user")
public interface UserAddressFeignClient {

    /** T11：按 ID 取本人地址（归属校验） */
    @GetMapping("/internal/users/{userId}/addresses/{addressId}")
    ApiResponse<AddressSnapshot> getAddress(@PathVariable("userId") Long userId,
                                            @PathVariable("addressId") Long addressId);

    /** T11：默认地址（addressId 缺省时兜底解析） */
    @GetMapping("/internal/users/{userId}/default-address")
    ApiResponse<AddressSnapshot> getDefaultAddress(@PathVariable("userId") Long userId);

    /** 地址快照（对齐 mall-user ShippingAddressVO 字段） */
    record AddressSnapshot(Long id, String receiverName, String phone,
                           String province, String city, String district, String detailAddress,
                           Boolean isDefault) {
    }
}

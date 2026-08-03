package com.cloudmart.user.service;

import com.cloudmart.user.dto.CreateAddressRequest;
import com.cloudmart.user.dto.UpdateAddressRequest;
import com.cloudmart.user.vo.ShippingAddressVO;

import java.util.List;

public interface AddressService {

    ShippingAddressVO createAddress(Long userId, CreateAddressRequest request);

    List<ShippingAddressVO> listAddresses(Long userId);

    ShippingAddressVO updateAddress(Long userId, Long addressId, UpdateAddressRequest request);

    void deleteAddress(Long userId, Long addressId);

    ShippingAddressVO setDefaultAddress(Long userId, Long addressId);

    ShippingAddressVO getDefaultAddress(Long userId);
}

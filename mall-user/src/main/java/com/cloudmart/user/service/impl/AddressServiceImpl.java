package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.converter.ShippingAddressConverter;
import com.cloudmart.user.dto.CreateAddressRequest;
import com.cloudmart.user.dto.UpdateAddressRequest;
import com.cloudmart.user.entity.ShippingAddress;
import com.cloudmart.user.repository.ShippingAddressMapper;
import com.cloudmart.user.service.AddressService;
import com.cloudmart.user.vo.ShippingAddressVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    private static final int MAX_ADDRESS_COUNT = 20;

    private final ShippingAddressMapper addressMapper;
    private final ShippingAddressConverter addressConverter;

    public AddressServiceImpl(ShippingAddressMapper addressMapper, ShippingAddressConverter addressConverter) {
        this.addressMapper = addressMapper;
        this.addressConverter = addressConverter;
    }

    @Override
    @Transactional
    public ShippingAddressVO createAddress(Long userId, CreateAddressRequest request) {
        long count = addressMapper.selectCount(
                new LambdaQueryWrapper<ShippingAddress>().eq(ShippingAddress::getUserId, userId)
        );
        if (count >= MAX_ADDRESS_COUNT) {
            throw new BusinessException("ADDRESS_LIMIT_EXCEEDED", "地址数量已达上限");
        }

        ShippingAddress entity = addressConverter.toEntity(request);
        entity.setUserId(userId);

        if (entity.getIsDefault() == 0 && count == 0) {
            entity.setIsDefault(1);
        }

        if (entity.getIsDefault() == 1) {
            clearDefaultFlag(userId);
        }

        addressMapper.insert(entity);
        return addressConverter.toVO(entity);
    }

    @Override
    public List<ShippingAddressVO> listAddresses(Long userId) {
        List<ShippingAddress> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<ShippingAddress>()
                        .eq(ShippingAddress::getUserId, userId)
                        .orderByDesc(ShippingAddress::getIsDefault)
                        .orderByDesc(ShippingAddress::getUpdatedAt)
        );
        return addressConverter.toVOList(addresses);
    }

    @Override
    @Transactional
    public ShippingAddressVO updateAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        ShippingAddress entity = getAddressAndVerifyOwnership(userId, addressId);

        ShippingAddress updated = addressConverter.toEntity(request);
        entity.setReceiverName(updated.getReceiverName());
        entity.setReceiverPhone(updated.getReceiverPhone());
        entity.setProvince(updated.getProvince());
        entity.setCity(updated.getCity());
        entity.setDistrict(updated.getDistrict());
        entity.setDetailAddress(updated.getDetailAddress());

        if (updated.getIsDefault() == 1 && entity.getIsDefault() != 1) {
            clearDefaultFlag(userId);
            entity.setIsDefault(1);
        }

        addressMapper.updateById(entity);
        return addressConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        ShippingAddress entity = getAddressAndVerifyOwnership(userId, addressId);
        boolean wasDefault = entity.getIsDefault() == 1;

        addressMapper.deleteById(addressId);

        if (wasDefault) {
            ShippingAddress earliest = addressMapper.selectOne(
                    new LambdaQueryWrapper<ShippingAddress>()
                            .eq(ShippingAddress::getUserId, userId)
                            .orderByAsc(ShippingAddress::getCreatedAt)
                            .last("LIMIT 1")
            );
            if (earliest != null) {
                earliest.setIsDefault(1);
                addressMapper.updateById(earliest);
            }
        }
    }

    @Override
    @Transactional
    public ShippingAddressVO setDefaultAddress(Long userId, Long addressId) {
        ShippingAddress entity = getAddressAndVerifyOwnership(userId, addressId);
        if (entity.getIsDefault() == 1) {
            return addressConverter.toVO(entity);
        }

        clearDefaultFlag(userId);
        entity.setIsDefault(1);
        addressMapper.updateById(entity);
        return addressConverter.toVO(entity);
    }

    @Override
    public ShippingAddressVO getDefaultAddress(Long userId) {
        ShippingAddress entity = addressMapper.selectOne(
                new LambdaQueryWrapper<ShippingAddress>()
                        .eq(ShippingAddress::getUserId, userId)
                        .eq(ShippingAddress::getIsDefault, 1)
        );
        return entity != null ? addressConverter.toVO(entity) : null;
    }

    private ShippingAddress getAddressAndVerifyOwnership(Long userId, Long addressId) {
        ShippingAddress entity = addressMapper.selectById(addressId);
        if (entity == null) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "地址不存在");
        }
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException("ADDRESS_ACCESS_DENIED", "无权操作此地址");
        }
        return entity;
    }

    private void clearDefaultFlag(Long userId) {
        addressMapper.update(
                new LambdaUpdateWrapper<ShippingAddress>()
                        .eq(ShippingAddress::getUserId, userId)
                        .eq(ShippingAddress::getIsDefault, 1)
                        .set(ShippingAddress::getIsDefault, 0)
        );
    }
}

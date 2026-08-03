package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.BrandDTO;
import com.cloudmart.product.dto.CreateBrandRequest;
import com.cloudmart.product.dto.UpdateBrandRequest;
import com.cloudmart.product.entity.Brand;
import com.cloudmart.product.repository.BrandMapper;
import com.cloudmart.product.service.BrandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BrandServiceImpl implements BrandService {

    private final BrandMapper brandMapper;

    public BrandServiceImpl(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    @Override
    @Transactional
    public BrandDTO createBrand(CreateBrandRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException("INVALID_NAME", "品牌名称不能为空");
        }

        Long existingCount = brandMapper.selectCount(
                new LambdaQueryWrapper<Brand>().eq(Brand::getName, request.name()));
        if (existingCount > 0) {
            throw new BusinessException("BRAND_EXISTS", "品牌名称已存在");
        }

        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setLogo(request.logo());
        brand.setDescription(request.description());
        brand.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        brand.setStatus(1);
        brandMapper.insert(brand);

        return toDTO(brand);
    }

    @Override
    @Transactional
    public BrandDTO updateBrand(Long id, UpdateBrandRequest request) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException("BRAND_NOT_FOUND", "品牌不存在");
        }

        if (request.name() != null && !request.name().isBlank()) {
            brand.setName(request.name());
        }
        if (request.logo() != null) {
            brand.setLogo(request.logo());
        }
        if (request.description() != null) {
            brand.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            brand.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            brand.setStatus(request.status());
        }
        brandMapper.updateById(brand);

        return toDTO(brand);
    }

    @Override
    public BrandDTO getBrand(Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException("BRAND_NOT_FOUND", "品牌不存在");
        }
        return toDTO(brand);
    }

    @Override
    public IPage<BrandDTO> listBrands(String name, Integer status, int page, int size) {
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(Brand::getName, name);
        }
        if (status != null) {
            wrapper.eq(Brand::getStatus, status);
        }
        wrapper.orderByAsc(Brand::getSortOrder).orderByDesc(Brand::getCreatedAt);

        IPage<Brand> pageResult = brandMapper.selectPage(new Page<>(page, size), wrapper);
        Page<BrandDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream().map(this::toDTO).toList());
        return dtoPage;
    }

    private BrandDTO toDTO(Brand brand) {
        return new BrandDTO(
                brand.getId(), brand.getName(), brand.getLogo(),
                brand.getDescription(), brand.getSortOrder(),
                brand.getStatus(), brand.getCreatedAt()
        );
    }
}

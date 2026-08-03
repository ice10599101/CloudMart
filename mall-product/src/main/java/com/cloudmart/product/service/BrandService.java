package com.cloudmart.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.product.dto.BrandDTO;
import com.cloudmart.product.dto.CreateBrandRequest;
import com.cloudmart.product.dto.UpdateBrandRequest;

public interface BrandService {

    BrandDTO createBrand(CreateBrandRequest request);

    BrandDTO updateBrand(Long id, UpdateBrandRequest request);

    BrandDTO getBrand(Long id);

    IPage<BrandDTO> listBrands(String name, Integer status, int page, int size);
}

package com.cloudmart.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.product.entity.Brand;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BrandMapper extends BaseMapper<Brand> {
}

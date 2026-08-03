package com.cloudmart.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}

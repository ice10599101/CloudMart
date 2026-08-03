package com.cloudmart.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Select("SELECT * FROM product_skus WHERE product_id = #{productId}")
    List<ProductSku> selectByProductId(Long productId);

    @Select("<script>SELECT * FROM product_skus WHERE product_id IN " +
            "<foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<ProductSku> selectByProductIds(Collection<Long> productIds);
}

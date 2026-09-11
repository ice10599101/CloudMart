package com.cloudmart.product.service;

import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.dto.CreateProductRequest;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchRequest;
import com.cloudmart.product.dto.ProductSearchResponse;
import com.cloudmart.product.dto.UpdateProductRequest;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(CreateProductRequest request);

    ProductDTO getProductById(Long id);

    /**
     * 批量查询 SKU 及其所属商品信息（名称/图片），供秒杀等跨服务 enrich 使用。
     */
    List<com.cloudmart.product.vo.SkuBatchItemVO> getSkuBatchInfo(List<Long> skuIds);

    ProductDTO updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);

    ProductSearchResponse searchProducts(ProductSearchRequest request);

    List<CategoryDTO> listCategories();

    CategoryDTO createCategory(String name, Long parentId);

    CategoryDTO updateCategory(Long id, String name, Long parentId, Integer sortOrder, Integer status);

    void deleteCategory(Long id);

    long getProductCount();
}

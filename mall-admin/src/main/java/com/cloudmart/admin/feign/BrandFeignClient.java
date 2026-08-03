package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(contextId = "brandFeignClient", name = "mall-product", path = "/admin/brands", fallbackFactory = BrandFeignClientFallbackFactory.class)
public interface BrandFeignClient {

    @GetMapping
    ApiResponse<Object> listBrands(@SpringQueryMap Map<String, Object> params);

    @GetMapping("/{id}")
    ApiResponse<Object> getBrand(@PathVariable("id") Long id);

    @PostMapping
    ApiResponse<Object> createBrand(@RequestBody Map<String, Object> body);

    @PutMapping("/{id}")
    ApiResponse<Object> updateBrand(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteBrand(@PathVariable("id") Long id);
}

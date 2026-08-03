package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AddSeckillProductRequest;
import com.cloudmart.admin.dto.feign.SeckillProductDTO;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(contextId = "seckillProductFeignClient", name = "mall-seckill", path = "/admin/seckill/products", fallbackFactory = SeckillProductFeignClientFallbackFactory.class)
public interface SeckillProductFeignClient {

    @GetMapping("/activity/{activityId}")
    ApiResponse<List<SeckillProductDTO>> listProductsByActivity(@PathVariable("activityId") Long activityId);

    @GetMapping("/{productId}")
    ApiResponse<SeckillProductDTO> getProduct(@PathVariable("productId") Long productId);

    @PostMapping("/{activityId}")
    ApiResponse<SeckillProductDTO> addProduct(
            @PathVariable("activityId") Long activityId, @RequestBody AddSeckillProductRequest request);

    @DeleteMapping("/{productId}")
    ApiResponse<Void> deleteProduct(@PathVariable("productId") Long productId);
}

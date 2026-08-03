package com.cloudmart.seckill.service;

import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;

import java.util.List;

public interface SeckillProductService {

    SeckillProductDTO addProduct(Long activityId, AddSeckillProductRequest request);

    SeckillProductDTO getProduct(Long productId);

    List<SeckillProductDTO> listProductsByActivity(Long activityId);

    void loadStockToRedis(Long activityId, Long productId);

    void loadAllStocksToRedis();
}

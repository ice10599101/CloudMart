package com.cloudmart.ai.service;

/**
 * 商品向量数据同步服务：将商品数据从 DB 同步到 ES 向量索引。
 * 通过 Embedding 模型将商品描述转为向量，存入 ElasticsearchVectorStore。
 */
public interface ProductVectorSyncService {

    /**
     * 全量同步商品数据到向量索引。
     */
    void fullSync();

    /**
     * 增量同步：仅同步指定商品。
     */
    void syncProduct(Long productId);

    /**
     * 从向量索引中删除指定商品。
     */
    void deleteProduct(Long productId);
}

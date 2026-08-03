package com.cloudmart.ai.service;

import com.cloudmart.ai.dto.VectorSearchResult;

import java.util.List;

/**
 * 基于 ES Dense Vector 的语义向量检索服务。
 * 将用户自然语言查询转换为 Embedding 向量，在 ES 中执行 KNN 相似度检索。
 */
public interface VectorSearchService {

    /**
     * 语义搜索商品：将查询文本转为向量后在 ES 中做 KNN 近邻搜索。
     *
     * @param query  用户自然语言查询
     * @param topK   返回最大数量
     * @return      按相似度排序的商品列表
     */
    List<VectorSearchResult> semanticSearch(String query, int topK);

    /**
     * 混合检索：同时使用向量相似度 + ES 全文检索，取并集后综合排序。
     * 向量检索召回语义相关但字面不匹配的结果，
     * 全文检索召回精确关键词匹配的结果，两者互补。
     *
     * @param query  用户自然语言查询
     * @param topK   返回最大数量
     * @return      综合排序的商品列表
     */
    List<VectorSearchResult> hybridSearch(String query, int topK);
}

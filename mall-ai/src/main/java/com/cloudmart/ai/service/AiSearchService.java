package com.cloudmart.ai.service;

import com.cloudmart.ai.dto.AiSearchResponse;

public interface AiSearchService {

    /**
     * AI 语义搜索商品：先通过 LLM 提取用户意图和关键词，
     * 再调用商品服务的 ES 搜索接口获取结果，最后由 LLM 生成推荐说明。
     * 当 LLM 不可用时，直接以用户输入作为关键词搜索。
     */
    AiSearchResponse search(Long userId, String query);
}

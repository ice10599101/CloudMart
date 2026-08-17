package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.HomeAggregationVO;

/**
 * 心愿宇宙首页聚合服务接口。
 *
 * <p>对应文档 2.18 节 GET /wish/home 首页聚合 API。</p>
 */
public interface HomeService {

    /**
     * 获取首页聚合数据。
     *
     * <p>Sprint 1.1 简化版规则：</p>
     * <ul>
     *   <li>worldTree = null（Sprint 2.1 上线）</li>
     *   <li>todayRecommend = 5 条（近 7 天 PUBLIC + APPROVED，互动量 0.5 + 时效性 0.3 + 多样性 0.2）</li>
     *   <li>myWishes = 已登录用户 3 条摘要；未登录返回空数组</li>
     *   <li>hotResonance = 5 条（复用 wish:hot:feed ZSet Top 5，按 support_count 降序）</li>
     *   <li>entries = {wishEntry: true, mapEntry: false, aiAssistantEntry: false}</li>
     * </ul>
     *
     * <p>缓存策略：</p>
     * <ul>
     *   <li>todayRecommend + hotResonance 共用 Redis ZSet {@code wish:hot:feed}（TTL 10min + 抖动 0-60s）</li>
     *   <li>myWishes 不缓存（用户个性化数据）</li>
     *   <li>缓存未命中时回源 DB 并回填缓存</li>
     * </ul>
     *
     * @param userId 当前用户 ID（可空，未登录时 myWishes 返回空数组）
     * @return 首页聚合 VO
     */
    HomeAggregationVO getHomeAggregation(Long userId);
}

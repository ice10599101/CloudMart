package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.SubmitFulfillmentRequest;
import com.cloudmart.wish.vo.WishFulfillmentSubmitVO;
import com.cloudmart.wish.vo.WishFulfillmentVO;

/**
 * 还愿服务接口（文档 2.4 节，Sprint 1.10）。
 *
 * <p>状态机（产品决策 2026-08-20，统一即时生效）：
 * ACTIVE/OVERDUE --提交还愿--> FULFILLED + 绽放果实 BLOOM；
 * 还愿故事走先发后审（audit_status=PENDING 仅标记，不阻断展示）。
 * FULFILLING 状态保留给后续 STRICT 审核流（本 Sprint 不落此状态）。</p>
 *
 * <p>联动（同一事务）：totalFulfilled + 1（历史事实不回退）、
 * activeWishes - 1、星光 +50（EARN/FULFILL，上限截断）、徽章判定
 * （FIRST_FULFILL）。</p>
 */
public interface FulfillmentService {

    /**
     * 提交还愿（仅作者，仅 ACTIVE/OVERDUE 心愿）。
     *
     * <p>uk_fulfillment_wish（1:1）与心愿状态条件 UPDATE 双保险防重复提交。
     * 并发冲突/已还愿 → WISH_NOT_FULFILLABLE（409）。</p>
     *
     * @param userId  当前用户 ID（即作者）
     * @param wishId  心愿 ID
     * @param request 还愿内容（story 必填）
     * @return 提交结果（含新获徽章与实际入账星光）
     */
    WishFulfillmentSubmitVO submitFulfillment(Long userId, Long wishId, SubmitFulfillmentRequest request);

    /**
     * 还愿详情（公开心愿任何人可看；PRIVATE/TREE_HOLE 非作者 404 防存在性探测）。
     *
     * @param wishId   心愿 ID
     * @param viewerId 当前用户 ID（网关注入，可空）
     * @return 还愿详情（含作者信息，Feign 降级为占位值）
     */
    WishFulfillmentVO getFulfillmentDetail(Long wishId, Long viewerId);
}

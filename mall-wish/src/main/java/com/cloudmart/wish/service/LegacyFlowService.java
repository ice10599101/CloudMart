package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.ContentFlowLog;
import com.cloudmart.wish.vo.InheritResultVO;

import java.util.List;

/**
 * 还愿传承 + 内容流转服务（Sprint 2.7，文档 2.7/2.8）。
 *
 * <p>错误码：403 WISH_NOT_AUTHOR / 409 WISH_NOT_FULFILLED /
 * WISH_ALREADY_INHERITED / 404 WISH_NOT_FOUND。</p>
 */
public interface LegacyFlowService {

    /**
     * 发起传承：定向推送给曾同求（SAME_WISH）用户，通知含还愿故事摘要；
     * 一条还愿仅允许一次传承。
     *
     * @param message 作者附言（可空，≤500 字）
     */
    InheritResultVO pushInherit(Long userId, Long wishId, String message);

    /**
     * 还愿成功后的异步内容流转：生成 community 帖子（《我的梦想实现记录》
     * 图文模板）。community 不可用时重试后记 FAILED，还愿主链路不受影响。
     * 必须在还愿事务提交后调用。
     */
    void submitContentFlow(Long wishId, Long fulfillmentId);

    /** 管理端重试失败的流转（单次尝试） */
    void retryFlow(Long logId);

    /** 还愿故事撤回时的状态同步：community 帖子同步隐藏 */
    void hideFlow(Long fulfillmentId);

    /** 管理端：流转日志列表（status 过滤可选，id 倒序） */
    List<ContentFlowLog> listFlowLogs(String status, int page, int size);

    /** 管理端：传承统计（触达率以推送成功率计） */
    LegacyStats getLegacyStats();

    /**
     * 传承统计行。
     *
     * @param pushedRate 推送成功率 = sum(pushed) / sum(target)（查看率需
     *                   mall-notification 阅读埋点，契约偏差已留档）
     */
    record LegacyStats(
            long inheritCount,
            long totalTargets,
            long totalPushed,
            double pushedRate,
            long flowSuccess,
            long flowFailed,
            long flowHidden) {
    }
}

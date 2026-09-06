package com.cloudmart.wish.service;

import com.cloudmart.wish.enums.ExpectedActionType;

import java.util.List;

/**
 * 预期管理服务（Sprint 2.5，文档 2.5 / 第三章 3.1）。
 *
 * <p>链路：00:30 OVERDUE 扫描流转后 → 本服务对刚到期心愿生成 AI 个性化
 * 引导文案并推送通知（含 3 选项：延长预期/调整目标/转入时间胶囊）→
 * 用户选择经埋点 API 记录（转化率分析）。</p>
 */
public interface ExpectedManagementService {

    /**
     * 对刚流转 OVERDUE 的心愿下发 AI 引导通知。
     *
     * <p>过滤规则（文档 2.5 限频策略）：</p>
     * <ul>
     *   <li>单用户每日最多 {@code expected.daily_limit}（默认 3）条，超过不推送</li>
     *   <li>通知偏好 CHECKIN_REMINDER×IN_APP 关闭的用户跳过</li>
     *   <li>未同意 AI 数据处理协议的用户使用模板文案（心愿内容不外发大模型服务）</li>
     *   <li>AI 生成失败降级模板文案（通知必达，文案是增强）</li>
     * </ul>
     *
     * @param wishes 刚流转 OVERDUE 的心愿详情（来自 scanOverdueWishesDetailed）
     */
    NotifyResult notifyExpiredWishes(List<WishService.OverdueWishInfo> wishes);

    /**
     * 记录用户对"心愿到期"通知 3 选项的选择（埋点，文档 2.5 数据回收）。
     *
     * @throws com.cloudmart.common.exception.BusinessException 404 WISH_NOT_FOUND 心愿不存在或非本人
     */
    void recordAction(Long userId, Long wishId, ExpectedActionType action);

    /**
     * 通知下发结果。
     *
     * @param notified            成功下发数（进入 MQ）
     * @param skippedByLimit      因每日限频跳过数
     * @param skippedByPreference 因通知偏好关闭跳过数
     */
    record NotifyResult(int notified, int skippedByLimit, int skippedByPreference) {
    }
}

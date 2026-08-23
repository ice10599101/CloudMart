package com.cloudmart.wish.service;

/**
 * AI 陪伴提醒服务（Sprint 2.5，文档 2.5 陪伴提醒 / 9.2 wish-ai-reminder）。
 *
 * <p>推送策略：</p>
 * <ul>
 *   <li>时机：用户本地时区 09:00（文档 9.2；XXL-Job 每小时触发，
 *       本扫描筛选本地时刻在 09 点段内的用户）</li>
 *   <li>对象：有 ACTIVE 心愿或 IN_PROGRESS AI 目标的用户</li>
 *   <li>频次：单用户每日最多 1 条（wish_ai_config 可配）</li>
 *   <li>免打扰：用户本地时刻落在免打扰时段（默认 22:00-08:00，可配）不推送</li>
 *   <li>偏好：AI_REMINDER×IN_APP 关闭的用户跳过（一键关闭即静默）</li>
 * </ul>
 */
public interface CompanionReminderService {

    /**
     * 扫描并推送陪伴提醒（每小时幂等执行）。
     */
    RemindResult scanAndRemind();

    /**
     * 扫描结果。
     *
     * @param candidates          候选用户数（有活跃心愿/目标）
     * @param reminded            成功下发数（进入 MQ）
     * @param skippedByLocalTime  非本地 09 点段跳过数
     * @param skippedByQuietHours 免打扰时段跳过数
     * @param skippedByLimit      每日限频跳过数
     * @param skippedByPreference 通知偏好关闭跳过数
     */
    record RemindResult(int candidates, int reminded, int skippedByLocalTime,
                        int skippedByQuietHours, int skippedByLimit, int skippedByPreference) {
    }
}

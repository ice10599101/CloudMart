package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AiConfigUpdateRequest;
import com.cloudmart.wish.vo.AiConfigVO;

import java.util.List;

/**
 * AI/提醒策略全局配置服务（Sprint 2.5，文档 2.5 管理后台）。
 *
 * <p>wish_ai_config 表存储全局策略（提醒频次/免打扰时段/预期管理限频/
 * 报告缓存时长），管理后台修改后 60s 内生效（短 TTL 缓存 + 更新主动失效），
 * 不改代码不重部署。</p>
 */
public interface AiConfigService {

    /** 配置键：陪伴提醒单用户每日上限 */
    String KEY_REMINDER_DAILY_LIMIT = "reminder.daily_limit";
    /** 配置键：免打扰开始（HH:mm，用户时区） */
    String KEY_QUIET_START = "reminder.quiet_start";
    /** 配置键：免打扰结束（HH:mm，用户时区） */
    String KEY_QUIET_END = "reminder.quiet_end";
    /** 配置键：预期管理通知单用户每日上限 */
    String KEY_EXPECTED_DAILY_LIMIT = "expected.daily_limit";
    /** 配置键：年度报告结果缓存时长（小时） */
    String KEY_ANNUAL_REPORT_TTL_HOURS = "annual_report.ttl_hours";

    /**
     * 读取字符串配置；键不存在或 DB 异常时返回默认值（Fail-Open，
     * 提醒策略读取失败不阻断业务）。
     */
    String getStringConfig(String key, String defaultValue);

    /**
     * 读取整型配置；解析失败返回默认值。
     */
    int getIntConfig(String key, int defaultValue);

    // ---------------- 管理端（mall-admin Feign 代理调用） ----------------

    /** 配置列表（管理后台提醒策略页） */
    List<AiConfigVO> listConfigs();

    /**
     * 更新配置值（管理后台实时生效：更新后主动失效缓存）。
     *
     * @throws BusinessException WISH_VALIDATION_ERROR 键不存在
     */
    AiConfigVO updateConfig(String key, AiConfigUpdateRequest request, Long adminUserId);
}

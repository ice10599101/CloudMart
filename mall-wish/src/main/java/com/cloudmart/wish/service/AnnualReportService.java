package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.AnnualReportVO;

/**
 * 年度报告服务（Sprint 2.5，文档 2.11 GET /wish/ai/annual-report）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>任务化：统计聚合同步返回（P95 &lt; 500ms），growthSummary 由
 *       异步任务调大模型生成（耗时目标 &lt; 60s）</li>
 *   <li>幂等：Redis SETNX 任务锁，生成中不重复提交；结果缓存命中直接返回</li>
 *   <li>可重试：异步任务失败清除任务锁，下次请求自动重新触发</li>
 *   <li>不持久化：报告结果仅存 Redis（TTL = annual_report.ttl_hours，默认 168h）</li>
 *   <li>降级：未同意 AI 协议 / 大模型失败 → 模板文案（报告必达）</li>
 * </ul>
 */
public interface AnnualReportService {

    /**
     * 获取年度报告（不存在时同步聚合统计 + 触发异步 AI 生成）。
     *
     * <p>首次请求：统计部分即时返回，growthSummary 为模板降级文案，
     * 同时提交异步 AI 任务；后续请求返回缓存中的 AI 版报告。</p>
     *
     * @param userId 用户 ID
     * @param year   报告年度（不可晚于当前年）
     * @return 年度报告 VO
     */
    AnnualReportVO getOrGenerateReport(Long userId, int year);
}

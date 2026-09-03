package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.DataExport;

/**
 * 用户数据导出服务（合规 34.2，四AB B5）。
 *
 * <p>链路：创建 PENDING 任务 → 异步聚合用户数据生成 JSON 落库 → SUCCESS（7 天
 * 有效期）→ 下载端点流式输出。失败置 FAILED，用户可重新发起。</p>
 */
public interface DataExportService {

    /** 创建导出任务并触发异步生成 */
    DataExport createExport(Long userId);

    /** 下载导出内容（归属校验 + 过期校验）；返回 null 表示内容不可用 */
    String loadContent(Long userId, Long taskId);

    /** 单任务查询（归属校验，他人任务按不存在处理） */
    DataExport getTask(Long userId, Long taskId);

    /** 本人任务列表 */
    java.util.List<DataExport> listTasks(Long userId);

    /** 过期任务惰性清理（查询/下载时顺带触发；内容超期即清空并置 FAILED） */
    void purgeExpired();
}

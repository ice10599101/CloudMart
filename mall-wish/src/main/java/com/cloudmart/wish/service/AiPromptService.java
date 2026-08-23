package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AiPromptCreateRequest;
import com.cloudmart.wish.dto.AiPromptStatusUpdateRequest;
import com.cloudmart.wish.entity.WishAiPrompt;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.vo.AiPromptVO;

import java.util.List;

/**
 * AI Prompt 模板服务（Sprint 2.5，文档 2.5 管理后台 Prompt 管理）。
 *
 * <p>核心策略：</p>
 * <ul>
 *   <li>运行时按 scene 读取 ACTIVE 模板，同 scene 多条按 traffic_percent
 *       加权分流（同一用户稳定分桶，A/B 测试语义）</li>
 *   <li>无 ACTIVE 模板 → 回退 {@code WishAiProperties} 代码内默认值
 *       （保证 DB 空表时功能可用）</li>
 *   <li>60s 内存缓存：Prompt 修改后最迟 1 分钟生效，不改代码不重部署</li>
 * </ul>
 */
public interface AiPromptService {

    /**
     * 获取指定场景对当前用户生效的 Prompt 正文。
     *
     * @param scene  AI 场景
     * @param userId 用户 ID（A/B 稳定分桶依据；null 时取首个 ACTIVE 模板）
     */
    String getActivePrompt(AiPromptScene scene, Long userId);

    // ---------------- 管理端（mall-admin Feign 代理调用） ----------------

    /** 管理端模板列表（含 DRAFT/ARCHIVED，scene 过滤可选） */
    List<AiPromptVO> listPrompts(AiPromptScene scene);

    /** 创建新版本模板（初始 DRAFT，激活后生效） */
    AiPromptVO createPrompt(AiPromptCreateRequest request, Long adminUserId);

    /** 更新模板状态（DRAFT→ACTIVE / ACTIVE→ARCHIVED；同 scene 至少保留一条 ACTIVE 由调用方保证） */
    AiPromptVO updatePromptStatus(Long promptId, AiPromptStatusUpdateRequest request, Long adminUserId);
}

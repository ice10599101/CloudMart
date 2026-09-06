package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AiConversationListQuery;
import com.cloudmart.wish.dto.TreeHoleMessageRequest;
import com.cloudmart.wish.vo.AiConversationVO;
import com.cloudmart.wish.vo.TreeHoleReplyVO;

import java.util.List;

/**
 * 树洞治愈服务（文档 2.11 / 30 章）。
 */
public interface TreeHoleService {

    /**
     * 发送树洞消息并获取 AI 治愈回复（文档 2.11：POST /wish/ai/tree-hole）。
     *
     * <p>处理链路：心愿校验（TREE_HOLE + 作者本人）→ AI 数据同意检查 →
     * 每日限频（10 次/日）→ 危机词本地拦截（不外发）→ PII 脱敏 →
     * 大模型生成回复 → 对话持久化（USER + ASSISTANT）。</p>
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         404 WISH_NOT_FOUND / 403 WISH_NOT_AUTHOR / 403 WISH_CONSENT_REQUIRED /
     *         400 WISH_VALIDATION_ERROR / 429 WISH_AI_RATE_LIMITED / 503 WISH_AI_UNAVAILABLE
     */
    TreeHoleReplyVO sendTreeHoleMessage(Long userId, TreeHoleMessageRequest request);

    /**
     * 查询当前用户的 AI 对话历史（cursor 分页，id 倒序）。
     *
     * @return 对话分页结果
     */
    ConversationPage listConversations(Long userId, AiConversationListQuery query);

    /**
     * cursor 分页结果（与互动列表一致的分页契约）。
     */
    record ConversationPage(List<AiConversationVO> records, String nextCursor, boolean hasMore) {
    }
}

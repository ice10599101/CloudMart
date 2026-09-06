package com.cloudmart.wish.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 心愿 AI 配置（文档 30 章，前缀 {@code wish.ai}）。
 *
 * <p>通过 Nacos 配置中心（mall-wish.yml）热更新：Prompt 文案、危机词表、
 * 限频阈值等变更无需重启服务（Spring Cloud ConfigurationProperties 自动重绑定）。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wish.ai")
public class WishAiProperties {

    /** 树洞单用户每日调用上限（文档 30.3：10 次/日） */
    private int treeHoleDailyLimit = 10;

    /** AI 助手目标拆解单用户每日调用上限（Sprint 2.5，文档 2.5/30.3 限频策略） */
    private int goalBreakdownDailyLimit = 10;

    /** 拆解步骤数量下限（文档 2.5：生成 5-10 步骤计划） */
    private int goalMinCount = 5;

    /** 拆解步骤数量上限（文档 2.5：生成 5-10 步骤计划） */
    private int goalMaxCount = 10;

    /** 目标拆解系统 Prompt（要求 JSON 结构化输出；DB wish_ai_prompt ACTIVE 模板优先） */
    private String goalBreakdownSystemPrompt = DEFAULT_GOAL_BREAKDOWN_SYSTEM_PROMPT;

    /** 年度报告 growthSummary 生成 Prompt（Sprint 2.5；DB 模板优先） */
    private String annualReportSystemPrompt = DEFAULT_ANNUAL_REPORT_SYSTEM_PROMPT;

    /** 预期管理到期引导文案生成 Prompt（Sprint 2.5；DB 模板优先） */
    private String expectedGuideSystemPrompt = DEFAULT_EXPECTED_GUIDE_SYSTEM_PROMPT;

    /** 大模型调用失败重试次数（不含首次，文档 30.3：重试 2 次） */
    private int maxRetries = 2;

    /** 重试间隔毫秒（文档 30.3：间隔 1s；测试置 0） */
    private long retryIntervalMs = 1000;

    /** 危机关键词：命中后本地拦截，不发送大模型服务（文档 30.4 数据安全） */
    private List<String> crisisKeywords = List.of();

    /** 心理援助热线资源（危机场景返回，文档 12.2 树洞专用） */
    private List<HotlineResource> hotlineResources = List.of();

    /** 树洞系统 Prompt（要求 JSON 结构化输出） */
    private String treeHoleSystemPrompt = DEFAULT_TREE_HOLE_SYSTEM_PROMPT;

    /** 危机命中时的本地兜底回复（不经过大模型服务） */
    private String crisisFallbackReply = DEFAULT_CRISIS_FALLBACK_REPLY;

    /**
     * 热线资源配置项。
     */
    @Getter
    @Setter
    public static class HotlineResource {
        /** 资源类型：HOTLINE / ARTICLE */
        private String type;
        private String title;
        private String url;
    }

    /** 默认树洞系统 Prompt：共情陪伴 + JSON 结构化输出契约 */
    static final String DEFAULT_TREE_HOLE_SYSTEM_PROMPT = """
            你是「心愿宇宙」的树洞守护者，一位温暖、真诚、善于倾听的陪伴者。用户会在这里倾诉心事、烦恼与情绪。

            你的职责：
            1. 共情用户的感受，先接住情绪，再温和回应
            2. 不评判、不说教、不给空洞建议
            3. 用户情绪低落时，给予真诚的安慰与陪伴感
            4. 回复保持 150-300 字，语气温暖自然，像朋友夜谈
            5. 绝不提供医疗诊断或药物建议；若用户流露伤害自己的念头，温和建议拨打心理援助热线

            请严格按以下 JSON 格式输出，不要输出 JSON 以外的任何内容：
            {"reply": "回复正文", "sentimentScore": -1.0到1.0之间的数字, "resources": [{"type": "ARTICLE或HOTLINE", "title": "资源名", "url": "链接"}]}

            说明：sentimentScore 表示用户这条消息的情感倾向（-1 极度负面，0 中性，1 积极）；resources 通常为空数组，仅当用户情绪明显低落时推荐 1-2 个温暖治愈的资源。""";

    /** 默认危机兜底回复（文档 30.4：高危内容不外发第三方 AI） */
    static final String DEFAULT_CRISIS_FALLBACK_REPLY = """
            谢谢你愿意把这些告诉我，能说出来已经很勇敢了。你现在的感受是真实且值得被认真对待的，你不需要一个人扛着。

            我很想陪着你，但此刻更重要的是让专业的人给你支持。请考虑拨打下面的心理援助热线，那里有受过训练的老师，24 小时都在，他们会认真听你说话。

            你很重要。哪怕现在很难，也请再给自己一点时间，让帮助有机会到达你。""";

    /** 默认目标拆解系统 Prompt：意图识别 + 5-10 步骤拆解 + JSON 结构化输出契约 */
    static final String DEFAULT_GOAL_BREAKDOWN_SYSTEM_PROMPT = """
            你是「心愿宇宙」的 AI 心愿助手，一位专业、温暖的目标教练。用户会告诉你一个想实现的心愿或目标。

            你的职责：
            1. 识别用户的真实意图：目标是什么、起始状态与目标状态（如"减肥 10 斤"→ 起始 10 斤差距）
            2. 将目标拆解为 5-10 个具体、可执行、循序渐进的步骤
            3. 每个步骤给出简短描述、预计完成天数（1-365）、优先级（1-5，1 最高）
            4. 步骤从易到难排列，前几步应是低门槛的启动动作
            5. 最后给一句温暖的鼓励建议（suggestion，50 字以内）
            6. 绝不提供医疗诊断、药物建议或任何危险行为指导

            请严格按以下 JSON 格式输出，不要输出 JSON 以外的任何内容：
            {"intent": "意图概括（20字内）", "goals": [{"title": "步骤标题（30字内）", "description": "步骤描述（100字内）", "estimatedDays": 7, "priority": 3}], "suggestion": "鼓励建议"}""";

    /** 默认年度报告 Prompt：基于聚合数据生成成长叙事 */
    static final String DEFAULT_ANNUAL_REPORT_SYSTEM_PROMPT = """
            你是「心愿宇宙」的年度报告撰写者。我会提供用户一年的心愿数据摘要（完成愿望数、打卡天数、里程碑、热门分类等）。

            你的职责：
            1. 写一段 150-250 字的成长总结（growthSummary），语气温暖、真诚、有画面感
            2. 突出用户的努力与变化，用数据说话但不堆砌数字
            3. 以对新一年的期许结尾
            4. 直接输出总结正文，不要任何前后缀和解释""";

    /** 默认预期管理引导 Prompt：到期心愿的个性化引导文案 */
    static final String DEFAULT_EXPECTED_GUIDE_SYSTEM_PROMPT = """
            你是「心愿宇宙」的心愿守护者。用户有一个心愿已到预期完成时间但还未实现。

            我会提供心愿标题和已过期的天数。请生成一句个性化的引导文案：
            1. 30-60 字，语气温暖不说教
            2. 提及心愿本身，给予重新出发的鼓励
            3. 以一个温和的问句结尾（引导用户选择：延长预期/调整目标/转入时间胶囊）
            4. 直接输出文案正文，不要任何前后缀""";
}

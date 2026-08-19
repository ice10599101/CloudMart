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

    /** DashScope 调用失败重试次数（不含首次，文档 30.3：重试 2 次） */
    private int maxRetries = 2;

    /** 重试间隔毫秒（文档 30.3：间隔 1s；测试置 0） */
    private long retryIntervalMs = 1000;

    /** 危机关键词：命中后本地拦截，不发送 DashScope（文档 30.4 数据安全） */
    private List<String> crisisKeywords = List.of();

    /** 心理援助热线资源（危机场景返回，文档 12.2 树洞专用） */
    private List<HotlineResource> hotlineResources = List.of();

    /** 树洞系统 Prompt（要求 JSON 结构化输出） */
    private String treeHoleSystemPrompt = DEFAULT_TREE_HOLE_SYSTEM_PROMPT;

    /** 危机命中时的本地兜底回复（不经过 DashScope） */
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
}

package com.cloudmart.pet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 社区宠物模块业务参数（Nacos {@code mall-pet.yml} 热更新，application.yml 提供默认值）。
 *
 * <p>数值权威在服务端：所有成长/互动/捞瓶/对战/聊天参数集中在此，
 * 客户端仅做展示，禁止携带数值字段。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pet")
public class PetProperties {

    private final Decay decay = new Decay();
    private final Level level = new Level();
    private final Interaction interaction = new Interaction();
    private final Bottle bottle = new Bottle();
    private final Battle battle = new Battle();
    private final Chat chat = new Chat();
    private final Proactive proactive = new Proactive();
    private final MultiPet multiPet = new MultiPet();
    private final Visit visit = new Visit();
    private final CommunityGrowth communityGrowth = new CommunityGrowth();

    /** 状态自然变化速率（每小时） */
    @Getter
    @Setter
    public static class Decay {
        private double hungerPerHour = 2.0;
        private double happinessPerHour = 1.0;
        private double energyRecoverPerHour = 3.0;
        private double cleanlinessPerHour = 1.5;
        /** 单次懒更新最大追溯时长（小时），防止长期离线一次扣光 */
        private double maxIdleHours = 48;
        /** 饥饿低于该阈值时心情额外衰减 */
        private int hungerMoodThreshold = 30;
        private double hungerMoodExtraPerHour = 1.0;
    }

    /** 升级曲线：expToNext(level) = expBase * level^1.5 */
    @Getter
    @Setter
    public static class Level {
        private int expBase = 100;
    }

    /** 基础互动数值 */
    @Getter
    @Setter
    public static class Interaction {
        private int feedDailyLimit = 5;
        private int feedHunger = 30;
        private int feedHappiness = 5;
        private int feedHp = 10;
        private int feedExp = 2;
        private int playEnergy = 15;
        private int playHappiness = 20;
        private int playExp = 8;
        private int cleanCleanliness = 40;
        private int cleanHappiness = 5;
        private int cleanExp = 2;
        private int restHunger = 5;
        /** 饿肚子触发主动提醒的阈值 */
        private int hungryRemindThreshold = 30;
    }

    /** 捞漂流瓶 */
    @Getter
    @Setter
    public static class Bottle {
        private long durationSeconds = 1800;
        private long cooldownSeconds = 600;
        private double baseSuccessRate = 0.70;
        private double agilityBonusRate = 0.005;
        private double levelBonusRate = 0.01;
        private double maxSuccessRate = 0.95;
    }

    /** 对战 */
    @Getter
    @Setter
    public static class Battle {
        private int pendingExpireHours = 48;
        private int winExp = 30;
        private int loseExp = 10;
        private int winCurrency = 20;
        private int maxRounds = 15;
    }

    /** 聊天 */
    @Getter
    @Setter
    public static class Chat {
        private int dailyLimit = 20;
        private int maxRetries = 2;
        private long retryIntervalMs = 1000;
        private int memoryLimit = 10;
        private int contextWindowSize = 12;
        /** 危机关键词本地拦截（命中不调 AI；与 mall-wish 树洞同表语义） */
        private List<String> crisisKeywords = List.of("自杀", "自残", "轻生", "不想活", "想死", "结束生命", "伤害自己", "了结自己");
        /** 简单问候固定行为命中后不再走 AI（省 token；正则前缀列表） */
        private List<String> fixedIntentKeywords = List.of("你叫什么", "你的名字", "你是谁", "在干嘛", "在干什么", "在做什么");
    }

    /** 主动消息频控 */
    @Getter
    @Setter
    public static class Proactive {
        private int dailyLimit = 3;
        private long minIntervalSeconds = 60;
    }

    /** 多宠物（原文档 §89 多种宠物）：一用户可拥有多只，日常玩法作用于主宠 */
    @Getter
    @Setter
    public static class MultiPet {
        /** 单用户宠物数量上限 */
        private int maxPets = 3;
    }

    /** 宠物串门（原文档 §1.1 宠物串门）：消耗精力换心情/经验，同一邻居每日一次 */
    @Getter
    @Setter
    public static class Visit {
        private int energyCost = 10;
        private int happinessGain = 8;
        private int expGain = 6;
        /** 每日串门次数上限 */
        private int dailyLimit = 3;
        /** 同一邻居串门冷却（小时） */
        private int neighborCooldownHours = 24;
    }

    /** 社区行为影响宠物成长（原文档 §1.1 "通过社区行为影响宠物成长"） */
    @Getter
    @Setter
    public static class CommunityGrowth {
        /** 每次社区互动（被点赞/评论/关注/收藏）给宠物加的经验 */
        private int expPerEvent = 1;
        /** 每日经验上限（Redis 计数，Fail-Open 时按上限放行） */
        private int dailyExpCap = 20;
    }
}

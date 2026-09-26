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

    /** 业务时区（每日任务/配额/陪伴的日归属，默认北京时间 00:00 重置，§7.3 基线） */
    private String businessZone = "Asia/Shanghai";

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
    private final Career career = new Career();
    private final Relation relation = new Relation();
    private final Home home = new Home();
    private final Friend friend = new Friend();
    private final Wall wall = new Wall();
    private final DailyQuest dailyQuest = new DailyQuest();
    private final Intimacy intimacy = new Intimacy();
    private final SkillSlots skillSlots = new SkillSlots();
    private final FeatureSwitches featureSwitches = new FeatureSwitches();

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

    /** 基础互动数值（2.4 防刷默认参数基线：喂食上限按用户共享；玩耍/休息收益日限额数据库权威） */
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
        /** 玩耍每日有收益次数（之后仅允许无收益动画互动，B06） */
        private int playRewardDailyLimit = 10;
        /** 休息定时活动时长（秒，B06：10 分钟定时活动） */
        private long restDurationSeconds = 600;
        /** 休息每日亲密度收益次数上限（B06） */
        private int restIntimacyDailyLimit = 3;
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

    /** 对战（2.4：每用户每日 10 场有收益；PvP 对同一用户每天至多 1 场有收益，双方分别计额度） */
    @Getter
    @Setter
    public static class Battle {
        private int pendingExpireHours = 48;
        private int winExp = 30;
        private int loseExp = 10;
        private int winCurrency = 20;
        private int maxRounds = 15;
        /** 每用户每日有收益对战上限（PVE+PVP 合计） */
        private int rewardDailyLimit = 10;
        /** 对同一对手用户每日有收益 PvP 上限 */
        private int pvpPerOpponentDailyLimit = 1;
    }

    /** 聊天 */
    @Getter
    @Setter
    public static class Chat {
        private int dailyLimit = 20;
        /** AI 生成调用日额度（与消息频控分离；固定/危机回复不消耗，B18） */
        private int aiDailyLimit = 20;
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

    /** 宠物职业（三期）：职业数值在 pet_career_config，这里只放风控参数 */
    @Getter
    @Setter
    public static class Career {
        /** 每日职业工作次数上限（Redis 计数，Fail-Open 放行） */
        private int dailyWorkLimit = 6;
    }

    /** 宠物关系（三期）：情侣 1v1，其余类型每只上限；亲密度只增不减 */
    @Getter
    @Setter
    public static class Relation {
        private int maxCouple = 1;
        private int maxBestie = 3;
        private int maxBrother = 3;
        private int maxConfidant = 3;
        /** 每日关系申请次数上限 */
        private int requestDailyLimit = 10;
        /** 关系亲密度等级阈值（升序，首项 0） */
        private List<Integer> levelThresholds = List.of(0, 50, 150, 400, 900);
        /** 关系亲密度等级名（与阈值一一对应） */
        private List<String> levelNames = List.of("初见", "熟络", "默契", "挚交", "生死之交");
        /** 好友互访给关系加的亲密度（双方均加） */
        private int intimacyGainVisit = 3;
        /** 留言给关系加的亲密度 */
        private int intimacyGainWall = 1;
        /** 双方对战给关系加的亲密度 */
        private int intimacyGainBattle = 2;
        /** 关系每日亲密度上限（防止互刷） */
        private int dailyIntimacyCap = 20;
    }

    /** 家园/房间（三期）：网格尺寸、舒适度加成与互访收益 */
    @Getter
    @Setter
    public static class Home {
        /** 网格宽（列数） */
        private int gridWidth = 4;
        /** 舒适度上限（B13：sum 封顶，发布时校验非负） */
        private int comfortCap = 100;
        /** 网格高（行数） */
        private int gridHeight = 3;
        /** 舒适度达到该值后享受休息加成 */
        private int comfortBonusThreshold = 60;
        /** 舒适加成：休息时额外恢复的心情（舒适度越高越多，封顶该值） */
        private int comfortRestHappinessBonus = 10;
        /** 每日首次进入自己家园：心情/经验 */
        private int dailyEnterHappiness = 6;
        private int dailyEnterExp = 5;
        /** 来访他人房间：访客获得的心情/经验 */
        private int visitRewardHappiness = 4;
        private int visitRewardExp = 5;
        /** 房间主人收到来访的经验（回礼） */
        private int hostVisitRewardExp = 2;
        /** 点赞他人房间：访客获得的经验 */
        private int likeRewardExp = 1;
        /** 每日访问他人房间次数上限 */
        private int dailyVisitLimit = 10;
        /** 每日点赞次数上限 */
        private int dailyLikeLimit = 20;
    }

    /** 好友互访（三期） */
    @Getter
    @Setter
    public static class Friend {
        /** 好友数量上限 */
        private int maxFriends = 50;
        /** 每日好友申请次数上限 */
        private int requestDailyLimit = 10;
        /** 每日互访次数上限 */
        private int dailyVisitLimit = 5;
        /** 互访：访客宠物获得的心情/经验 */
        private int visitRewardHappiness = 5;
        private int visitRewardExp = 6;
        /** 互访：好友宠物获得的经验（被访问回礼） */
        private int visitHostExp = 2;
    }

    /** 留言墙（三期） */
    @Getter
    @Setter
    public static class Wall {
        /** 留言最大长度 */
        private int maxLength = 120;
        /** 每日留言条数上限（跨房间累计） */
        private int dailyPostLimit = 10;
        /** 每日点赞次数上限 */
        private int dailyLikeLimit = 30;
        /** 单页条数上限 */
        private int maxPageSize = 50;
    }

    /** 每日任务（三期）：任务数值在 pet_daily_quest_config，这里放全清奖励 */
    @Getter
    @Setter
    public static class DailyQuest {
        /** 全清宝箱：经验 */
        private int chestExp = 60;
        /** 全清宝箱：星光 */
        private int chestCurrency = 80;
    }

    /** 亲密度与陪伴时长（三期）：数值只增不减，等级提供经验加成 */
    @Getter
    @Setter
    public static class Intimacy {
        private int feedGain = 2;
        private int playGain = 3;
        private int cleanGain = 2;
        private int restGain = 1;
        private int chatGain = 1;
        private int workGain = 4;
        private int studyGain = 4;
        private int bottleGain = 5;
        private int battleGain = 3;
        private int visitGain = 3;
        private int roomGain = 2;
        private int wallGain = 1;
        private int questGain = 2;
        /** 陪伴：每累计多少秒加 1 点亲密度 */
        private int companionSecondsPerPoint = 600;
        /** 陪伴：每日计入的亲密度点数上限 */
        private int companionDailyPointCap = 8;
        /** 陪伴：每日计入的秒数上限（超出不计，防止挂机） */
        private int companionDailyCapSeconds = 7200;
        /** 陪伴心跳会话失效间隔（秒）：超过该间隔无有效心跳则会话失效，不补计中断区间（B05） */
        private long companionSessionTimeoutSeconds = 90;
        /** 亲密度等级阈值（升序，首项 0） */
        private List<Integer> levelThresholds = List.of(0, 100, 300, 700, 1500, 3000, 6000, 12000);
        /** 亲密度等级名（与阈值一一对应） */
        private List<String> levelNames = List.of("初识", "熟悉", "亲近", "亲密", "知心", "挚友", "家人", "灵魂伴侣");
        /** 每级亲密度提供的经验加成（0.01 = 1%） */
        private double expBonusPerLevel = 0.01;
        /** 经验加成上限 */
        private double maxExpBonus = 0.10;
        /** 亲密度升级奖励星光 = base × 新等级序号 */
        private int levelRewardStarlightBase = 120;
    }

    /** 技能槽模式（B12：配置关闭的能力——默认关闭保持"已学技能全部生效"既有行为；开启需执行分配迁移） */
    @Getter
    @Setter
    public static class SkillSlots {
        private boolean enabled = false;
        private int activeSlots = 1;
        private int passiveSlots = 2;
    }

    /**
     * §9.3 可回退功能开关（默认全开）：
     * 关闭定时休息回落为旧即时恢复；关闭幂等交易走旧直连链路（失去重试/补偿，仅应急）；
     * 关闭新增玩法则对应端点返回 PET_FEATURE_DISABLED。已支付/已达成操作的查询与领取不受开关影响。
     */
    @Getter
    @Setter
    public static class FeatureSwitches {
        private boolean timedRest = true;
        private boolean walletIdempotent = true;
        private boolean minigame = true;
        private boolean custody = true;
        private boolean cooperation = true;
        private boolean onboarding = true;
        private boolean collection = true;
        private boolean diary = true;
    }
}

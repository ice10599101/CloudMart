package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.vo.PetDailyQuestItemVO;
import com.cloudmart.pet.vo.PetDailyQuestVO;

/**
 * 宠物每日任务服务（三期）。
 *
 * <p>进度来源是<b>业务埋点</b>：{@link #record} 由各玩法在自己的写入路径调用
 * （喂食/玩耍/清洁/休息/打工/读书/捞瓶/对战/串门/聊天/职业/好友互访/留言/陪伴/布置），
 * 客户端只发意图、不上报进度——与"数值服务端权威"一致。</p>
 *
 * <p>任务行按日惰性生成（{@code uk_pet_daily_quest} 幂等 + 唯一索引兜底并发），
 * 领奖走 CAS（COMPLETE → CLAIMED）幂等；全部任务领取后可领"全清宝箱"，
 * 宝箱用同一张表的保留任务码 {@code DAILY_CHEST} 记录（复用 CAS 幂等机制，不另建表）。</p>
 */
public interface PetDailyQuestService {

    /** 今日任务列表（含进度、领奖状态、全清宝箱状态；首次访问自动生成当日任务行） */
    PetDailyQuestVO list(Long userId);

    /** 领取单个任务奖励（未完成 409 / 重复领取 409；经验本地 + 星光 Feign，失败整体回滚） */
    PetDailyQuestItemVO claim(Long userId, String questCode);

    /**
     * B15/R13：一键领奖——逐项独立事务（本方法非事务编排，单项经代理各自成事务），
     * 返回逐项终态 + 宝箱独立评估；禁止只返回一个 success 提示。
     */
    com.cloudmart.pet.vo.ClaimAllResult claimAll(Long userId);

    /** 领取全清宝箱（有未领取任务时 409 PET_QUEST_CHEST_NOT_READY） */
    PetDailyQuestVO claimChest(Long userId);

    /**
     * 业务埋点：按口径累加进度（原子 UPDATE，天然幂等；任务不存在/未启用时静默跳过）。
     * 不抛业务异常——埋点失败绝不能影响主玩法。
     */
    void record(Pet pet, PetQuestType type, int amount);

    /**
     * R32：事实驱动的进度埋点（pet_quest_event_receipt）——
     * uk(questType, eventId) 保证同一业务事实至多消费一次；进度计入事实归属业务日：
     * 事实日=今天走常规累加；历史事实仅在该日任务行已存在时补算（不为历史日凭空生成、
     * 不把历史行为加到今天）；事实过期收据置 SKIPPED_STALE 可由管理端重放。
     *
     * @return true=本次实际计入进度；false=重复事实/过期跳过/埋点失败
     */
    boolean recordFact(Pet pet, PetQuestType type, String eventId,
                       java.time.LocalDateTime sourceTime, int amount);

    /** R32 管理端补算：重放一条 SKIPPED_STALE 收据（只允许重放已有事实，不能手工改进度） */
    com.cloudmart.pet.entity.PetQuestEventReceipt replayReceipt(Long receiptId);

    /** R32 管理端：收据查询（userId/questCode/status 过滤 + 分页） */
    java.util.List<com.cloudmart.pet.entity.PetQuestEventReceipt> receipts(
            Long userId, String questCode, String status, int page, int size);

    /**
     * §8.2 管理后台：取消某日任务实例（受审计命令）——IN_PROGRESS/COMPLETE 可取消，
     * CLAIMED 拒绝（奖励已发出，收回须走调账补偿链路）；reason 必填随行落库。
     */
    com.cloudmart.pet.entity.PetDailyQuest cancelQuestInstance(Long petId, java.time.LocalDate questDate,
                                                               String questCode, String operatorName, String reason);
}

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

    /**
     * 今日任务列表（调用方已持有主宠快照的聚合读专用，语义同 {@link #list(Long)}，
     * 避免同一次聚合请求内重复加载主宠）。pet 必须是调用方按用户校验过的主宠。
     */
    PetDailyQuestVO listForPet(Pet pet);

    // ---------------- PET-09：任务集查询与按集领取（C14：setId/questId 均为实体 ID，唯一领奖路径） ----------------

    /**
     * 按集领取：setId 为任务集实体 ID，questId 为任务实体 ID（C14：不再接受 code 别名）。
     * 归属/宽限截止/任务行归属同事务校验，领取绑定集的原宠物。
     */
    PetDailyQuestItemVO claimInSet(Long userId, Long setId, Long questId);

    /** 任务集详情（深链接/刷新/冲突恢复；宽限期内/历史集均可查询） */
    PetDailyQuestVO questSetDetail(Long userId, Long setId);

    /** 本人任务集列表：status=ACTIVE 当前集 / EXPIRED 历史集 / 空 全部 */
    java.util.List<com.cloudmart.pet.entity.PetDailyQuestSet> questSets(Long userId, String status);

    /** 按集一键领取：逐项独立事务绑定该集（切宠/宽限期不错对象），宝箱独立评估 */
    com.cloudmart.pet.vo.ClaimAllResult claimAllInSet(Long userId, Long setId);

    /** 按集领取宝箱：绑定集的宠物与冻结快照，宽限期内可领 */
    PetDailyQuestVO claimChestInSet(Long userId, Long setId);

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

    /** PET-10：调度重试到期 FAILED 回执（投影与 APPLIED 同事务；单轮小批） */
    int retryFailedReceipts();

    /** R32 管理端：收据查询（userId/questCode/status 过滤 + 分页） */
    /** PET-22/T49：收据分页查询（真实 total，允许翻页处理全部待处置回执） */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.cloudmart.pet.entity.PetQuestEventReceipt> receipts(
            Long userId, String questCode, String status, int page, int size);

    /**
     * §8.2 管理后台：取消某日任务实例（受审计命令）——IN_PROGRESS/COMPLETE 可取消，
     * CLAIMED 拒绝（奖励已发出，收回须走调账补偿链路）；reason 必填随行落库。
     */
    com.cloudmart.pet.entity.PetDailyQuest cancelQuestInstance(Long petId, java.time.LocalDate questDate,
                                                               String questCode, String operatorName, String reason);
}

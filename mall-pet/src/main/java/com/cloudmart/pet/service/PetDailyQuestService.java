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

    /** B15：批量领取全部已完成项（逐项独立 CAS 与幂等，单项失败跳过可重试） */
    java.util.List<PetDailyQuestItemVO> claimAll(Long userId);

    /** 领取全清宝箱（有未领取任务时 409 PET_QUEST_CHEST_NOT_READY） */
    PetDailyQuestVO claimChest(Long userId);

    /**
     * 业务埋点：按口径累加进度（原子 UPDATE，天然幂等；任务不存在/未启用时静默跳过）。
     * 不抛业务异常——埋点失败绝不能影响主玩法。
     */
    void record(Pet pet, PetQuestType type, int amount);
}

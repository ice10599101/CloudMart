package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.vo.PetIntimacyVO;

/**
 * 亲密度与陪伴时长服务（三期）。
 *
 * <p>设计要点：{@link #gain} 只修改内存中的宠物实体（不落库），由调用方在自己那一次
 * 乐观锁写入里一并持久化——避免同一事务内两次 {@code updateById} 造成 @Version 冲突；
 * 独立入口 {@link #heartbeat} 自己负责落库。</p>
 *
 * <p>亲密度等级只提供经验加成与称号展示（不发星光）：等级提升会发宠物口吻通知，
 * 让"陪伴"这件事被看见，但不引入跨服务写，保证任何玩法事务都不会因它失败。</p>
 */
public interface PetIntimacyService {

    /** 业务埋点：按来源加亲密度（数值来自配置），返回本次提升的等级数（0 = 未升级） */
    int gain(Pet pet, PetIntimacySource source);

    /** 陪伴心跳：累计陪伴秒数并换算亲密度（日上限内），落库并返回本次新增亲密度 */
    int heartbeat(Long userId, int seconds);

    /** 亲密度等级（1 起；阈值取自配置） */
    int levelOf(int intimacy);

    /** 等级名（如 初识/熟悉/…/灵魂伴侣） */
    String levelName(int level);

    /** 距下一等级还需点数（满级返回 0） */
    int toNext(int intimacy);

    /** 亲密度带来的经验加成（0.01 = 1%，上限见配置） */
    double expBonus(Pet pet);

    /** 亲密度与陪伴概览（前端展示：等级/进度/陪伴时长/加成） */
    PetIntimacyVO overview(Long userId);
}

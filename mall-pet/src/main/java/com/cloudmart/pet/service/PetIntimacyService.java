package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.vo.PetCompanionSessionVO;
import com.cloudmart.pet.vo.PetIntimacyVO;

/**
 * 亲密度与陪伴时长服务（三期 + B05）。
 *
 * <p>B05 变更：{@link #gain} <b>原子落库</b>（{@code SET intimacy = intimacy + N} 目标列写入，
 * 不经全实体 updateById，避免旧值覆盖并发收益）；经验奖励为 0 的调用也保证亲密度持久化。
 * 陪伴计时以服务端会话为权威：开始建立基准、心跳按服务端时钟差累计有效时长
 * （超过失效间隔不补计中断区间）、停止结算有效窗口；客户端上报秒数仅作参考不参与计算。
 * 多端共用每用户至多一条 ACTIVE 会话（时间不翻倍），重复心跳按序号去重。</p>
 *
 * <p>陪伴积分公式：{@code entitled = min(floor(todayAcceptedSeconds / secondsPerPoint), dailyPointCap)}；
 * {@code grant = max(0, entitled - todayGrantedPoints)}——时长、积分、已发计数同事务保存。</p>
 */
public interface PetIntimacyService {

    /** 业务埋点：按来源原子加亲密度（数值来自配置，落库），返回本次提升的等级数（0 = 未升级） */
    int gain(Pet pet, PetIntimacySource source);

    /**
     * 陪伴心跳（B05）：服务端会话计时权威。无有效会话时自动建立基准（本次不计时）；
     * 携带单调递增 seq 时重复请求幂等返回；超过失效间隔的会话结束且不补计中断区间。
     *
     * @param seconds 客户端上报秒数（仅参考，服务端按时钟差计算，不参与收益判定）
     * @param seq     客户端会话内序号（可空；提供时用于重放去重）
     * @return 会话与今日累计视图
     */
    PetCompanionSessionVO heartbeat(Long userId, Integer seconds, Long seq);

    /** 显式结束陪伴会话：结算有效窗口内尚未计入的时间，正常结束（幂等） */
    PetCompanionSessionVO stopSession(Long userId);

    /** 亲密度等级（1 起；阈值取自配置） */
    int levelOf(int intimacy);

    /** 等级名（如 初识/熟悉/…/灵魂伴侣） */
    String levelName(int level);

    /** 距下一等级还需点数（满级返回 0） */
    int toNext(int intimacy);

    /** 亲密度带来的经验加成（0.01 = 1%，上限见配置） */
    double expBonus(Pet pet);

    /** 亲密度与陪伴概览（前端展示：等级/进度/陪伴时长/加成；跨天惰性归零展示正确今日值） */
    PetIntimacyVO overview(Long userId);
}

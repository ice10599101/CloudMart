package com.cloudmart.pet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.pet.vo.PetPveBossVO;
import com.cloudmart.pet.vo.PetPveRunVO;

import java.util.List;

/**
 * 协作 PVE 副本（§6/R03/R04）：turnNo/version CAS 状态机 + 独立回合明细 +
 * 占用行唯一约束 + 可恢复奖励（pet_reward_claim，bizType=PVE）。
 */
public interface PetPveService {

    /** 发起副本（选 Boss；一人同时仅一个 OPEN/FIGHTING 副本——数据库占用行约束） */
    PetPveRunVO start(Long userId, String bossCode);

    /** Boss 配置列表（6.2 R03：GET /bosses，前端动态加载，不硬编码 HP/数值） */
    List<PetPveBossVO> bosses();

    /** 可加入副本列表（OPEN，他人发起） */
    Page<PetPveRunVO> openRuns(int page, int size);

    /** 队友加入（OPEN→FIGHTING；占用行 + CAS） */
    PetPveRunVO join(Long userId, Long runId);

    /**
     * 攻击一回合（R03）：nextActorUserId 判定行动权；requestKey 重放原回合结果；
     * CAS (id, version, status) 推进 turnNo/bossHp/HP；回合明细落独立表。
     */
    PetPveRunVO attack(Long userId, Long runId, String requestKey);

    /** 发起人取消 OPEN 副本（释放占用） */
    PetPveRunVO cancel(Long userId, Long runId);

    /** 我的副本（发起或参与，倒序分页） */
    Page<PetPveRunVO> mine(Long userId, int page, int size);

    /** 副本详情（回合明细分页读取） */
    PetPveRunVO detail(Long userId, Long runId);

    /**
     * 定时收口（R03）：FIGHTING 超总时限 → FAILED（终态释放占用）；
     * OPEN 超 24h → EXPIRED。多实例安全（CAS 条件更新）。
     *
     * @return 收口副本数
     */
    int closeExpiredRuns();

    /**
     * 定时补发（R04）：WON 且 reward_status=PENDING 的副本逐用户重试领奖
     * （pet_reward_claim uk 保证每人每场至多一次；全部 COMPLETED → SETTLED）。
     *
     * @return 本次结算完成的副本数
     */
    int settlePendingRewards();
}

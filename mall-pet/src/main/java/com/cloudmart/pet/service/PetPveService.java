package com.cloudmart.pet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.pet.vo.PetPveRunVO;

/**
 * 协作 PVE 副本（§6）：2 人协战 Boss。
 */
public interface PetPveService {

    /** 发起副本（选 Boss；一人同时仅一个 OPEN/FIGHTING 副本） */
    PetPveRunVO start(Long userId, String bossCode);

    /** 可加入副本列表（OPEN，他人发起，排除自己发起的） */
    Page<PetPveRunVO> openRuns(int page, int size);

    /** 队友加入（OPEN→FIGHTING；快照落库） */
    PetPveRunVO join(Long userId, Long runId);

    /** 发起人攻击一回合（双宠轮流对 Boss + Boss 反击；CAS 推进防并发） */
    PetPveRunVO attack(Long userId, Long runId);

    /** 我的副本（发起或参与，倒序 20 条） */
    Page<PetPveRunVO> mine(Long userId, int page, int size);

    /** 副本详情 */
    PetPveRunVO detail(Long userId, Long runId);
}

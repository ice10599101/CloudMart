package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.StartStudyRequest;
import com.cloudmart.pet.dto.StartWorkRequest;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetJobVO;
import com.cloudmart.pet.vo.PetStudyVO;

import java.util.List;

/**
 * 统一活动服务：打工 + 读书共用 pet_activity 状态机
 *（IN_PROGRESS → COMPLETED → CLAIMED；COMPLETED 超 72h 未领取 EXPIRED）。
 */
public interface PetActivityService {

    /** 打工岗位列表（含当前宠物是否满足接单条件） */
    List<PetJobVO> listJobs(Long userId);

    /** 开始打工：互斥校验（每用户进行中活动唯一）→ 等级/精力/饥饿校验 → 冻结规则快照 → 立即扣消耗 */
    PetActivityVO startWork(Long userId, StartWorkRequest request);

    /** 领取打工奖励（兼容入口：稳定取本人最新一条 WORK），奖励归属实际执行宠物 */
    PetActivityVO claimWork(Long userId);

    /** 读书课程列表 */
    List<PetStudyVO> listStudies(Long userId);

    /** 开始读书（同打工：冻结快照） */
    PetActivityVO startStudy(Long userId, StartStudyRequest request);

    /** 领取读书奖励（兼容入口）：经验 + 智力 */
    PetActivityVO claimStudy(Long userId);

    /**
     * 按 activityId 幂等领取（B03/B09 唯一任务归属入口）：校验活动属于当前用户，
     * 奖励归 activity.petId；按类型分派 WORK/STUDY 本地结算，CAREER_WORK/BOTTLE_FISHING
     * 委托对应服务；星光经统一操作记录幂等发放（结果未知返回"结算中"）。
     */
    PetActivityVO claimActivity(Long userId, Long activityId);

    /** 统一活动列表（B09）：进行中/待领取/已领取/已过期，支持宠物过滤与分页 */
    List<PetActivityVO> listActivities(Long userId, String status, Long petId, int page, int size);

    /** 过期清理：COMPLETED 超时未领取置 EXPIRED（定时器兜底调用，返回处理条数） */
    int expireStaleClaims();
}

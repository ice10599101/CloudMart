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

    /** 开始打工：互斥校验（进行中活动唯一）→ 等级/精力/饥饿校验 → 立即扣消耗 */
    PetActivityVO startWork(Long userId, StartWorkRequest request);

    /** 领取打工奖励：CAS 幂等（重复领取 409）；经验 + 星光（Feign，失败整体回滚可重试） */
    PetActivityVO claimWork(Long userId);

    /** 读书课程列表 */
    List<PetStudyVO> listStudies(Long userId);

    /** 开始读书 */
    PetActivityVO startStudy(Long userId, StartStudyRequest request);

    /** 领取读书奖励：经验 + 智力 */
    PetActivityVO claimStudy(Long userId);

    /** 过期清理：COMPLETED 超时未领取置 EXPIRED（定时器兜底调用，返回处理条数） */
    int expireStaleClaims();
}

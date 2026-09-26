package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.ApplyCareerRequest;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetCareerItemVO;
import com.cloudmart.pet.vo.PetCareerVO;

/**
 * 宠物职业服务（三期）。
 *
 * <p>职业 = 长期工作：{@link #startWork}/{@link #claimWork} 复用统一活动状态机
 * （{@code pet_activity.activity_type = CAREER_WORK}，服务端时间为权威、到点自动完成），
 * 累计工作次数满足「次数 + 等级」后由 {@link #promote} 晋升到同路线下一阶，
 * 晋升消耗星光（B01：操作记录幂等，结果未知返回"结算中"）。</p>
 */
public interface PetCareerService {

    /** 职业面板（当前职业 + 职业列表 + 工作历史 + 进行中任务） */
    PetCareerVO status(Long userId);

    /** 入职/转职（仅第一阶；同路线高阶职业必须先晋升，直接调用接口也不允许跳级） */
    PetCareerItemVO apply(Long userId, ApplyCareerRequest request);

    /** 开始职业工作（与打工共用"每用户一次只能做一件事"的约束） */
    PetActivityVO startWork(Long userId);

    /** 领取职业工作奖励（兼容入口：稳定取本人最新一条 CAREER_WORK；CAS 幂等，记工作次数与累计收入） */
    PetActivityVO claimWork(Long userId);

    /** 按 activityId 领取职业工作奖励（B03 唯一任务归属：奖励归 activity.petId） */
    PetActivityVO claimByActivity(Long userId, PetActivity activity);

    /** 晋升到同路线下一阶（当前职业/次数/等级/智力/星光统一校验，只扣一次费用） */
    PetCareerItemVO promote(Long userId);
}

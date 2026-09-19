package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.ApplyCareerRequest;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetCareerItemVO;
import com.cloudmart.pet.vo.PetCareerVO;

/**
 * 宠物职业服务（三期）。
 *
 * <p>职业 = 长期工作：{@link #startWork}/{@link #claimWork} 复用统一活动状态机
 * （{@code pet_activity.activity_type = CAREER_WORK}，服务端时间为权威、到点自动完成），
 * 累计工作次数满足「次数 + 等级」后由 {@link #promote} 晋升到同路线下一阶，
 * 晋升消耗星光（mall-wish 内部端点，失败整体回滚）。</p>
 */
public interface PetCareerService {

    /** 职业面板（当前职业 + 职业列表 + 工作历史 + 进行中任务） */
    PetCareerVO status(Long userId);

    /** 入职/转职（同路线高阶职业必须先晋升，不允许直接跳级） */
    PetCareerItemVO apply(Long userId, ApplyCareerRequest request);

    /** 开始职业工作（与打工共用"一次只能做一件事"的约束） */
    PetActivityVO startWork(Long userId);

    /** 领取职业工作奖励（CAS 幂等；记工作次数与累计收入） */
    PetActivityVO claimWork(Long userId);

    /** 晋升到同路线下一阶（工作次数/等级/星光三条件） */
    PetCareerItemVO promote(Long userId);
}

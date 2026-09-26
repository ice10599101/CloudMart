package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetBottleStatusVO;

/**
 * 宠物捞漂流瓶服务（复用 mall-wish 漂流瓶池，不建第二套瓶子——原文档 §14-20）。
 */
public interface PetBottleFishingService {

    /** 捞瓶状态：任务剩余时间/可领取/冷却/估算成功率/解锁区域 */
    PetBottleStatusVO status(Long userId);

    /** 开始捞瓶（30 分钟任务；冷却独立于领取状态；进行中活动互斥） */
    PetActivityVO start(Long userId);

    /** 领取捞瓶结果（兼容入口）：CAS 幂等；结果含 outcome 与 bottleId（CAUGHT 时跳转漂流瓶页查看） */
    PetActivityVO claim(Long userId);

    /** 按 activityId 领取捞瓶结果（B03/B11：按活动 ID 查询/领取，旧结果不被遮蔽） */
    PetActivityVO claimByActivity(Long userId, PetActivity activity);

    /**
     * 任务结算：成功率 roll → 调 mall-wish 捞瓶 → 落 pet_bottle_record → 通知。
     * 供领取/状态查询惰性调用与定时扫描器调用；CAS 保证只结算一次。
     *
     * @return 结算后的活动（COMPLETED 状态，result 含 outcome）
     */
    PetActivityVO settle(Long userId);
}

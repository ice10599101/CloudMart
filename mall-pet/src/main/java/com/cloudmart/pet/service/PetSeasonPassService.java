package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetSeasonPassVO;

/**
 * 赛季通行证（§6）：进行中赛季的任务经验累积 + 档位奖励领取。
 */
public interface PetSeasonPassService {

    /** 我的通行证（无进行中赛季返回 seasonId=null 的空视图） */
    PetSeasonPassVO myPass(Long userId);

    /** 领取档位奖励（达标且未领取；CAS 追加 claimed_tiers；宠物币入账，最终档含皮肤） */
    PetSeasonPassVO claimTier(Long userId, int tier);

    /** 任务/宝箱领取时累积通行证经验（无进行中赛季忽略；失败不阻断主流程） */
    void addExp(Long userId, int exp);
}

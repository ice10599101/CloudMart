package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetVO;

/**
 * 宠物基础互动：喂食/玩耍/清洁/休息。
 * 全部服务端结算（数值/经验/限频），客户端（Cocos/宿主）只发意图。
 */
public interface PetInteractionService {

    /** 喂食：饥饿+30、心情+5、HP+10、经验+2；每日 5 次（Redis 降级 Fail-Open）；饱食已满 409 */
    PetVO feed(Long userId);

    /** 玩耍：精力-15、心情+20、经验+8；精力不足 409 */
    PetVO play(Long userId);

    /** 清洁：清洁度+40、心情+5、经验+2；清洁度已高 409 */
    PetVO clean(Long userId);

    /** 休息：精力回满、HP 回满、饥饿-5；打工/读书/捞瓶进行中 409 */
    PetVO rest(Long userId);
}

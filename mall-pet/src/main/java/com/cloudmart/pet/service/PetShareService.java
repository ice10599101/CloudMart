package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetShareCardVO;

/**
 * 宠物动态分享服务（原文档 §36：宠物动态卡片 + §80 宠物分享/成就分享）。
 * 文案由服务端按宠物当前状态生成，用户复制后可发布到社区（不自动发帖）。
 */
public interface PetShareService {

    /**
     * 生成分享卡片。
     *
     * @param type 卡片类型：LEVEL_UP（成长）/ ACHIEVEMENT（最近成就）/ BOTTLE（捞瓶战果）/ BATTLE（对战战绩）
     */
    PetShareCardVO buildCard(Long userId, String type);
}

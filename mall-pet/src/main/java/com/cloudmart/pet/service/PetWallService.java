package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.PostWallMessageRequest;
import com.cloudmart.pet.dto.ReplyWallMessageRequest;
import com.cloudmart.pet.vo.PetWallLikeVO;
import com.cloudmart.pet.vo.PetWallMessageVO;
import com.cloudmart.pet.vo.PetWallPageVO;

/**
 * 宠物留言墙服务（三期）。
 *
 * <p>规则：家园未公开的宠物不可留言（403）；留言 1-120 字、每日有上限；
 * 作者可删自己的留言，墙主人可删自家墙上的留言（软删，管理端可审核）；
 * 点赞 uk 幂等；主人回复挂在一级留言下（两层结构）。</p>
 *
 * <p>收益：留言给我自己的宠物加亲密度（带宠物出门社交），
 * 若与墙主人宠物已建立关系则同时加关系亲密度；被留言方收到宠物口吻提醒。</p>
 */
public interface PetWallService {

    /** 某宠物留言墙分页（一级留言 + 主人回复；分页参数非法时服务端兜底） */
    PetWallPageVO list(Long userId, Long petId, Integer page, Integer size);

    /** 留言（家园未公开 403；每日超限 429；内容非法 400） */
    PetWallMessageVO post(Long userId, PostWallMessageRequest request);

    /** 主人回复（只有墙主人可回复自己墙上的一级留言） */
    PetWallMessageVO reply(Long userId, ReplyWallMessageRequest request);

    /** 删除留言（作者或墙主人；软删保留审核轨迹） */
    void delete(Long userId, Long messageId);

    /** 点赞/取消点赞（uk 幂等；每日有上限） */
    PetWallLikeVO like(Long userId, Long messageId);
}

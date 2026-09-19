package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetFriendPanelVO;
import com.cloudmart.pet.vo.PetFriendVO;
import com.cloudmart.pet.vo.PetFriendVisitResultVO;

/**
 * 宠物好友服务（三期）：好友申请/确认/解除 + 好友互访。
 *
 * <p>好友是双向关系（确认时双方各落一行 ACTIVE，{@code uk_pet_friend} 幂等）；
 * 互访复用家园的房间结算（{@link PetHomeService#visitFriendRoom}），
 * 额外给双方累计互访次数、加关系亲密度（若两只宠物已建立关系）、
 * 完成"出门交朋友"每日任务，并给好友发宠物口吻提醒。</p>
 */
public interface PetFriendService {

    /** 好友面板（好友 + 收到申请 + 我发出的申请 + 今日互访余量） */
    PetFriendPanelVO panel(Long userId);

    /** 申请加好友（对方已有待处理申请时视为互相确认：直接成为好友） */
    PetFriendVO request(Long userId, Long friendUserId);

    /** 同意好友申请 */
    PetFriendVO accept(Long userId, Long friendUserId);

    /** 拒绝好友申请 */
    PetFriendVO reject(Long userId, Long friendUserId);

    /** 删除好友（双向解除） */
    void remove(Long userId, Long friendUserId);

    /** 好友互访（每日次数上限；双方获得经验，好友关系与关系亲密度同步增长） */
    PetFriendVisitResultVO visit(Long userId, Long friendUserId);
}

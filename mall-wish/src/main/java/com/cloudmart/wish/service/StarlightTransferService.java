package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.StarlightTransferVO;

import java.util.List;

/**
 * 星光转赠（§6）：好友间对转（spend/earn 同事务）+ 日限额风控。
 */
public interface StarlightTransferService {

    /** 转赠（非自转、好友、单笔 10..100、日累计 ≤200）；返回转赠流水 */
    StarlightTransferVO transfer(Long fromUserId, Long toUserId, int amount, String message);

    /** 我近 20 条转赠记录（发出+收到合并倒序） */
    List<StarlightTransferVO> myTransfers(Long userId);
}

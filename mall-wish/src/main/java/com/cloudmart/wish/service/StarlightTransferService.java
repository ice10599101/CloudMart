package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.StarlightTransferVO;

import java.util.List;

/**
 * 星光转赠（§6/R06）：守恒、原子限额、持久幂等。
 */
public interface StarlightTransferService {

    /** 日转出额度视图（6.2 R06 /limits 契约） */
    record TransferLimits(int dailyCap, int usedToday, int remainingToday, java.time.LocalDate businessDate) {
    }

    /** 分页转赠记录（发出+收到合并倒序） */
    record TransferPage(List<StarlightTransferVO> records, long total) {
    }

    /**
     * 转赠（非自转、好友、单笔 10..100、日累计 ≤200 原子占用、等额对转守恒）。
     * requestKey 持久幂等：同键重放返回原结果；同键异载荷 409。
     */
    StarlightTransferVO transfer(Long fromUserId, Long toUserId, int amount, String message, String requestKey);

    /** 我的转赠记录（分页，发出+收到合并倒序） */
    TransferPage myTransfers(Long userId, int page, int pageSize);

    /** 我的日转出额度（cap/已用/剩余，按服务端 Asia/Shanghai 业务日） */
    TransferLimits transferLimits(Long userId);
}

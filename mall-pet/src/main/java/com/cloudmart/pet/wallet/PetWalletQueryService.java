package com.cloudmart.pet.wallet;

import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletTransaction;

import java.util.List;

/**
 * 宠物币钱包查询服务（W01/§8.2 钱包与交易接口）。
 *
 * <p>查询无副作用：查看流水/余额不触发领奖、不建账扣款；金额以十进制字符串
 * 契约由序列化层（Long→String）保证，接口层不做数值运算。</p>
 */
public interface PetWalletQueryService {

    /** 本人账户视图（懒创建期初 0；FROZEN 显示原因由 status 表达） */
    PetWalletAccount getWallet(Long userId);

    /**
     * 流水游标分页（默认倒序，新→旧）。
     *
     * @param cursor    上一页最后一条的 id（null=从头）
     * @param direction 可空过滤 EARN/SPEND/REFUND/ADJUSTMENT
     * @param bizType   可空过滤业务类型
     */
    List<PetWalletTransaction> listTransactions(Long userId, Long cursor, int size,
                                                String direction, String bizType);

    /** 按原交易查退款流水（订单详情的退款关系展示） */
    List<PetWalletTransaction> listRefundsOf(Long originalTransactionId);
}

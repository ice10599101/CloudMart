package com.cloudmart.wish.vo;

import java.util.List;

/**
 * 星光兑换结果（B05）：以服务端原子扣减为准，返回扣款后余额；
 * balanceAfter 为 null 表示免费资产未产生扣款。
 * B04：本结果为持久幂等凭证（wish_operation 重放依据）。
 */
public record ExchangeResultVO(
        Long id,
        Long assetId,
        Integer balanceAfter,
        Integer spentAmount
) {
}

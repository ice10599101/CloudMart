package com.cloudmart.wish.service;

/**
 * 宠物模块星光支持服务（mall-pet 内部端点专用）。
 *
 * <p>存在意义：{@link UserStatService#earnStarlight}/{@link UserStatService#spendStarlight}
 * 以 {@code Propagation.MANDATORY} 声明，必须由外层事务驱动（原有实现由业务 Service 开启事务）。
 * 内部端点直接调用会抛 {@code IllegalTransactionStateException}，因此这里提供带事务边界的
 * 适配层，保证"发星光/扣星光 + 流水"原子提交。</p>
 */
public interface PetSupportService {

    /** 发放宠物奖励星光（PET_REWARD 流水），返回实际入账量（余额上限可能截断） */
    int earnForPet(Long userId, int amount, Long refId);

    /** 扣减宠物消费星光（PET_SHOP 流水），余额不足抛 WISH_STARLIGHT_INSUFFICIENT，返回扣减后余额 */
    int spendForPet(Long userId, int cost, Long refId);

    /** 星光余额（商城展示用；只读，无事务要求） */
    int petStarlightBalance(Long userId);
}

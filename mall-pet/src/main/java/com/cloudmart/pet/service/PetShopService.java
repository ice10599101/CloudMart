package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetShopVO;

/**
 * 宠物商城（原文档 §89：装备/皮肤/技能书/食物统一售卖；币域 PET_COIN）。
 *
 * <p>价格、加成、门槛全部来自服务端配置表；客户端只提交"买什么"。
 * C13 起购买统一走 {@code POST /pet/purchases}（PetPurchaseApplicationService 编排：
 * 扣款/交付/幂等同事务），本接口仅保留目录查询。</p>
 */
public interface PetShopService {

    /** 商城列表（商品 + 余额 + 拥有/可购状态；余额币种随钱包模式切换） */
    PetShopVO shop(Long userId);
}

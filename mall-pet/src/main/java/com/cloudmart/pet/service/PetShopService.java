package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopVO;

/**
 * 宠物商城（原文档 §89：装备/皮肤/技能书/食物统一售卖；币域 PET_COIN）。
 *
 * <p>价格、加成、门槛全部来自服务端配置表；客户端只提交"买什么"。
 * R02 收口后购买统一委托 {@code PetPurchaseApplicationService}（本接口仅做
 * 目录查询与兼容结果组装，扣款/交付/幂等在统一购买链内编排）。</p>
 */
public interface PetShopService {

    /** 商城列表（商品 + 余额 + 拥有/可购状态；余额币种随钱包模式切换） */
    PetShopVO shop(Long userId);

    /** 购买（R02：委托统一购买服务；缺幂等键 400，同键重放返回原结果） */
    PetInventoryItemVO buy(Long userId, BuyItemRequest request);
}

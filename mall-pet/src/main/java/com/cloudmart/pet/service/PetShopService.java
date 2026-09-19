package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopVO;

/**
 * 宠物商城（原文档 §89 宠物商城：装备/皮肤/技能书统一售卖，货币为星光）。
 *
 * <p>价格、加成、门槛全部来自服务端配置表；客户端只提交"买什么"。
 * 扣星光走 mall-wish 内部端点（PET_SHOP 流水），失败整体回滚不产出物品。</p>
 */
public interface PetShopService {

    /** 商城列表（商品 + 我的星光余额 + 拥有/可购状态） */
    PetShopVO shop(Long userId);

    /** 购买（先落背包再扣星光：扣减失败回滚，不会出现"付了钱没拿到东西"） */
    PetInventoryItemVO buy(Long userId, BuyItemRequest request);
}

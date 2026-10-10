package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetFeedEntitlement;
import com.cloudmart.pet.repository.PetFeedEntitlementMapper;
import com.cloudmart.pet.service.PetLinkedProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 实物商品联动（§6）：双倍喂食权益。
 *
 * <p>链路：用户在商城购买实物零食 → 收货后获得 coupon 兑换码（运营配发，coupon 侧
 * /user-coupons/exchange 校验归属本人并发放用户券）→ 兑换成功后调本服务 redeem 发放
 * 权益 → 用户下一次 feedItem 使用该食物时效果 x2（consumeOne CAS 消耗）。</p>
 *
 * <p>权益与具体食物解耦（itemCode 冗余记录主食物，消耗时不强制匹配——用户体验优先，
 * 运营口径为"买零食送一次双倍喂食"）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetLinkedProductServiceImpl implements PetLinkedProductService {

    private final PetFeedEntitlementMapper entitlementMapper;

    @Override
    @Transactional
    public Long redeem(Long userId, String redemptionCode, Long userCouponId, String itemCode) {
        if (redemptionCode == null || redemptionCode.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "兑换码不能为空");
        }
        PetFeedEntitlement entitlement = new PetFeedEntitlement();
        entitlement.setUserId(userId);
        entitlement.setRedemptionCode(redemptionCode.trim().toUpperCase());
        entitlement.setUserCouponId(userCouponId);
        entitlement.setItemCode(itemCode == null || itemCode.isBlank() ? "ANY" : itemCode.trim());
        entitlement.setUsed(false);
        try {
            entitlementMapper.insert(entitlement);
            log.info("双倍喂食权益已发放: userId={}, code={}, entitlementId={}",
                    userId, entitlement.getRedemptionCode(), entitlement.getId());
            return entitlement.getId();
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该兑换码已核销过");
        }
    }

    @Override
    public boolean hasUsableEntitlement(Long userId) {
        return entitlementMapper.selectCount(new LambdaQueryWrapper<PetFeedEntitlement>()
                .eq(PetFeedEntitlement::getUserId, userId)
                .eq(PetFeedEntitlement::getUsed, false)) > 0;
    }

    @Override
    @Transactional
    public void consumeOne(Long userId, String itemCode) {
        int updated = entitlementMapper.update(null, new LambdaUpdateWrapper<PetFeedEntitlement>()
                .eq(PetFeedEntitlement::getUserId, userId)
                .eq(PetFeedEntitlement::getUsed, false)
                .set(PetFeedEntitlement::getUsed, true)
                .set(PetFeedEntitlement::getUsedAt, LocalDateTime.now()));
        if (updated > 0) {
            log.info("双倍喂食权益已消耗: userId={}, food={}", userId, itemCode);
        }
    }
}

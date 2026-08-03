package com.cloudmart.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.TieredPromotion;
import com.cloudmart.marketing.entity.TieredPromotionRule;
import com.cloudmart.marketing.repository.TieredPromotionMapper;
import com.cloudmart.marketing.repository.TieredPromotionRuleMapper;
import com.cloudmart.marketing.service.TieredPromotionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TieredPromotionServiceImpl implements TieredPromotionService {

    private final TieredPromotionMapper promotionMapper;
    private final TieredPromotionRuleMapper ruleMapper;
    private final MarketingConverter converter;

    public TieredPromotionServiceImpl(TieredPromotionMapper promotionMapper,
                                      TieredPromotionRuleMapper ruleMapper,
                                      MarketingConverter converter) {
        this.promotionMapper = promotionMapper;
        this.ruleMapper = ruleMapper;
        this.converter = converter;
    }

    @Override
    @Transactional
    public TieredPromotionDTO createPromotion(CreateTieredPromotionRequest request) {
        if (request.startTime().isAfter(request.endTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "开始时间不能晚于结束时间");
        }
        if (request.rules() == null || request.rules().isEmpty()) {
            throw new BusinessException("RULES_REQUIRED", "阶梯规则不能为空");
        }
        validateRules(request.rules());

        TieredPromotion entity = converter.toEntity(request);
        promotionMapper.insert(entity);

        for (TieredRuleRequest rule : request.rules()) {
            TieredPromotionRule ruleEntity = converter.toEntity(entity.getId(), rule);
            ruleMapper.insert(ruleEntity);
        }

        return getPromotion(entity.getId());
    }

    @Override
    @Transactional
    public TieredPromotionDTO enablePromotion(Long id) {
        TieredPromotion promotion = promotionMapper.selectById(id);
        if (promotion == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
        }
        if ("ENDED".equals(promotion.getStatus())) {
            throw new BusinessException("PROMOTION_ENDED", "已结束的活动不可启用");
        }
        if (LocalDateTime.now().isAfter(promotion.getEndTime())) {
            promotion.setStatus("ENDED");
            promotionMapper.updateById(promotion);
            throw new BusinessException("PROMOTION_EXPIRED", "活动已过期");
        }
        promotion.setStatus("ENABLED");
        promotionMapper.updateById(promotion);
        return getPromotion(id);
    }

    @Override
    @Transactional
    public TieredPromotionDTO disablePromotion(Long id) {
        TieredPromotion promotion = promotionMapper.selectById(id);
        if (promotion == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
        }
        promotion.setStatus("DISABLED");
        promotionMapper.updateById(promotion);
        return getPromotion(id);
    }

    @Override
    public TieredPromotionDTO getPromotion(Long id) {
        TieredPromotion promotion = promotionMapper.selectById(id);
        if (promotion == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
        }
        List<TieredPromotionRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<TieredPromotionRule>()
                .eq(TieredPromotionRule::getPromotionId, id)
                .orderByAsc(TieredPromotionRule::getMinAmount)
        );
        TieredPromotionDTO dto = converter.toDTO(promotion);
        return new TieredPromotionDTO(
            dto.id(), dto.name(), dto.description(),
            dto.startTime(), dto.endTime(), dto.status(), dto.createdAt(),
            converter.toRuleDTOList(rules)
        );
    }

    @Override
    public IPage<TieredPromotionDTO> listPromotions(String status, int page, int size) {
        LambdaQueryWrapper<TieredPromotion> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(TieredPromotion::getStatus, status);
        }
        wrapper.orderByDesc(TieredPromotion::getCreatedAt);
        IPage<TieredPromotion> pageResult = promotionMapper.selectPage(new Page<>(page, size), wrapper);

        Page<TieredPromotionDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream().map(p -> {
            List<TieredPromotionRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<TieredPromotionRule>()
                    .eq(TieredPromotionRule::getPromotionId, p.getId())
                    .orderByAsc(TieredPromotionRule::getMinAmount)
            );
            TieredPromotionDTO dto = converter.toDTO(p);
            return new TieredPromotionDTO(
                dto.id(), dto.name(), dto.description(),
                dto.startTime(), dto.endTime(), dto.status(), dto.createdAt(),
                converter.toRuleDTOList(rules)
            );
        }).toList());
        return dtoPage;
    }

    @Override
    public CalculateDiscountResult calculateDiscount(CalculateDiscountRequest request) {
        if (request.orderAmount() == null || request.orderAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return new CalculateDiscountResult(null, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        TieredPromotion promotion = promotionMapper.selectById(request.promotionId());
        if (promotion == null || !"ENABLED".equals(promotion.getStatus())) {
            return new CalculateDiscountResult(null, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(promotion.getStartTime()) || now.isAfter(promotion.getEndTime())) {
            return new CalculateDiscountResult(null, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        List<TieredPromotionRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<TieredPromotionRule>()
                .eq(TieredPromotionRule::getPromotionId, request.promotionId())
                .le(TieredPromotionRule::getMinAmount, request.orderAmount())
                .orderByDesc(TieredPromotionRule::getMinAmount)
        );

        if (rules.isEmpty()) {
            return new CalculateDiscountResult(null, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        TieredPromotionRule bestRule = rules.getFirst();
        return new CalculateDiscountResult(bestRule.getId(), bestRule.getMinAmount(), bestRule.getDiscountAmount(), true);
    }

    private void validateRules(List<TieredRuleRequest> rules) {
        BigDecimal prevMin = BigDecimal.ZERO;
        BigDecimal prevDiscount = BigDecimal.ZERO;
        for (TieredRuleRequest rule : rules) {
            if (rule.minAmount().compareTo(prevMin) <= 0) {
                throw new BusinessException("INVALID_RULE_ORDER", "阶梯规则最低金额必须递增");
            }
            if (rule.discountAmount().compareTo(rule.minAmount()) >= 0) {
                throw new BusinessException("INVALID_DISCOUNT", "优惠金额不能大于等于最低消费金额");
            }
            if (rule.discountAmount().compareTo(prevDiscount) <= 0) {
                throw new BusinessException("INVALID_DISCOUNT_ORDER", "阶梯优惠金额必须递增");
            }
            prevMin = rule.minAmount();
            prevDiscount = rule.discountAmount();
        }
    }
}

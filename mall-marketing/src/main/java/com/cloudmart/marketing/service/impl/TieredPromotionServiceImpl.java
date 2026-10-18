package com.cloudmart.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.TieredPromotion;
import com.cloudmart.marketing.entity.TieredPromotionRule;
import com.cloudmart.marketing.feign.ProductSkuFeignClient;
import com.cloudmart.marketing.repository.TieredPromotionMapper;
import com.cloudmart.marketing.repository.TieredPromotionRuleMapper;
import com.cloudmart.marketing.service.TieredPromotionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TieredPromotionServiceImpl implements TieredPromotionService {

    private final TieredPromotionMapper promotionMapper;
    private final TieredPromotionRuleMapper ruleMapper;
    private final MarketingConverter converter;
    /** T10：试算取价（SKU+数量 → 购物篮金额），价格必须取商品权威值 */
    private final ProductSkuFeignClient productSkuFeignClient;

    public TieredPromotionServiceImpl(TieredPromotionMapper promotionMapper,
                                      TieredPromotionRuleMapper ruleMapper,
                                      MarketingConverter converter,
                                      ProductSkuFeignClient productSkuFeignClient) {
        this.promotionMapper = promotionMapper;
        this.ruleMapper = ruleMapper;
        this.converter = converter;
        this.productSkuFeignClient = productSkuFeignClient;
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

    @Override
    public TieredMatchResult matchBest(TieredMatchRequest request) {
        if (request == null || request.orderAmount() == null
                || request.orderAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return TieredMatchResult.none();
        }
        LocalDateTime now = LocalDateTime.now();
        List<TieredPromotion> active = promotionMapper.selectList(
                new LambdaQueryWrapper<TieredPromotion>()
                        .eq(TieredPromotion::getStatus, "ENABLED")
                        .le(TieredPromotion::getStartTime, now)
                        .ge(TieredPromotion::getEndTime, now));
        if (active.isEmpty()) {
            return TieredMatchResult.none();
        }
        // 单次查询拉回所有生效活动的可达规则，内存按活动分组取各活动最高阶梯后再全局择优
        // （多活动重叠：优惠金额 desc → 门槛 desc → promotionId asc，确定性可复现）
        Map<Long, TieredPromotion> promoIndex = active.stream()
                .collect(java.util.stream.Collectors.toMap(TieredPromotion::getId, p -> p, (a, b) -> a));
        List<TieredPromotionRule> reachable = ruleMapper.selectList(
                new LambdaQueryWrapper<TieredPromotionRule>()
                        .in(TieredPromotionRule::getPromotionId, promoIndex.keySet())
                        .le(TieredPromotionRule::getMinAmount, request.orderAmount()));
        TieredMatchResult best = TieredMatchResult.none();
        for (TieredPromotionRule rule : reachable) {
            TieredPromotion promotion = promoIndex.get(rule.getPromotionId());
            if (promotion == null || isSameOrWorse(rule, best)
                    || (currentPromotionWins(best, rule, promotion))) {
                continue;
            }
            best = new TieredMatchResult(promotion.getId(), promotion.getName(), rule.getId(),
                    rule.getMinAmount(), rule.getDiscountAmount(), true);
        }
        return best;
    }

    /** 候选规则不优于当前最优：金额更小，或金额相同且门槛更低，或再相同活动 ID 更大。 */
    private boolean isSameOrWorse(TieredPromotionRule candidate, TieredMatchResult current) {
        if (!current.matched()) {
            return false;
        }
        int byDiscount = candidate.getDiscountAmount().compareTo(current.discountAmount());
        if (byDiscount != 0) {
            return byDiscount < 0;
        }
        int byMin = candidate.getMinAmount().compareTo(current.minAmount());
        if (byMin != 0) {
            return byMin < 0;
        }
        return candidate.getPromotionId() > current.promotionId();
    }

    /** 同活动内已选过一条规则时，跳过该活动的其他可达规则（每活动只取最高阶梯）。 */
    private boolean currentPromotionWins(TieredMatchResult current, TieredPromotionRule candidate,
                                        TieredPromotion promotion) {
        return current.matched() && current.promotionId().equals(promotion.getId())
                && candidate.getDiscountAmount().compareTo(current.discountAmount()) <= 0;
    }

    @Override
    public TieredPreviewResult preview(Long promotionId, TieredPreviewRequest request) {
        if (request == null) {
            throw new BusinessException("INVALID_ARGUMENT", "试算请求不能为空");
        }
        TieredPromotion promotion = promotionMapper.selectById(promotionId);
        if (promotion == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
        }
        BigDecimal orderAmount = resolvePreviewAmount(request);
        LocalDateTime previewAt = request.previewAt() == null ? LocalDateTime.now() : request.previewAt();

        List<TieredPromotionRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<TieredPromotionRule>()
                        .eq(TieredPromotionRule::getPromotionId, promotionId)
                        .orderByAsc(TieredPromotionRule::getMinAmount));

        TieredPromotionRule best = null;
        int matchedIndex = -1;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).getMinAmount().compareTo(orderAmount) <= 0) {
                best = rules.get(i);
                matchedIndex = i;
            }
        }

        List<String> reasons = new ArrayList<>();
        if (best == null) {
            reasons.add(rules.isEmpty()
                    ? "该活动未配置阶梯规则，用户侧不会命中"
                    : "购物篮 ¥" + plain(orderAmount) + " 未达最低门槛 ¥" + plain(rules.getFirst().getMinAmount()));
        } else {
            reasons.add("购物篮 ¥" + plain(orderAmount) + " 达到门槛 ¥" + plain(best.getMinAmount())
                    + "，命中第 " + (matchedIndex + 1) + " 档，减 ¥" + plain(best.getDiscountAmount()));
        }

        List<String> blockingReasons = new ArrayList<>();
        if (!"ENABLED".equals(promotion.getStatus())) {
            blockingReasons.add("活动状态为 " + promotion.getStatus() + "，用户侧不可用");
        }
        if (promotion.getStartTime() != null && previewAt.isBefore(promotion.getStartTime())) {
            blockingReasons.add("试算时点早于活动开始时间 " + promotion.getStartTime());
        }
        if (promotion.getEndTime() != null && previewAt.isAfter(promotion.getEndTime())) {
            blockingReasons.add("试算时点晚于活动结束时间 " + promotion.getEndTime());
        }

        boolean matched = best != null;
        return new TieredPreviewResult(
                matched,
                blockingReasons.isEmpty(),
                promotion.getId(),
                promotion.getName(),
                promotion.getStatus(),
                matched ? best.getId() : null,
                matched ? best.getMinAmount() : null,
                matched ? best.getDiscountAmount() : BigDecimal.ZERO,
                orderAmount,
                List.copyOf(reasons),
                List.copyOf(blockingReasons),
                promotion.getUpdatedAt(),
                previewAt);
    }

    /** T10：购物篮金额解析——优先 SKU+数量（权威价），否则使用显式金额。 */
    private BigDecimal resolvePreviewAmount(TieredPreviewRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            if (request.items().size() > 100) {
                throw new BusinessException("PREVIEW_TOO_MANY_ITEMS", "单次试算最多 100 行");
            }
            Map<Long, Integer> merged = new LinkedHashMap<>();
            for (TieredPreviewRequest.PreviewItem item : request.items()) {
                if (item == null || item.skuId() == null) {
                    throw new BusinessException("SKU_NOT_FOUND", "SKU 不能为空");
                }
                merged.merge(item.skuId(), item.quantity(), Integer::sum);
            }
            for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
                if (entry.getValue() > 999) {
                    throw new BusinessException("INVALID_QUANTITY",
                            "SKU " + entry.getKey() + " 合并数量超过单行上限（999）");
                }
            }
            ApiResponse<List<ProductSkuFeignClient.SkuBatchItem>> response =
                    productSkuFeignClient.getSkusBatch(List.copyOf(merged.keySet()));
            if (response == null || !response.success() || response.data() == null) {
                throw new BusinessException("PREVIEW_PRODUCT_UNAVAILABLE", "商品服务暂不可用，无法试算");
            }
            Map<Long, ProductSkuFeignClient.SkuBatchItem> skuIndex = new LinkedHashMap<>();
            for (ProductSkuFeignClient.SkuBatchItem sku : response.data()) {
                if (sku != null && sku.skuId() != null) {
                    skuIndex.put(sku.skuId(), sku);
                }
            }
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
                ProductSkuFeignClient.SkuBatchItem sku = skuIndex.get(entry.getKey());
                if (sku == null) {
                    throw new BusinessException("SKU_NOT_FOUND", "商品不存在: SKU " + entry.getKey());
                }
                if (!Integer.valueOf(1).equals(sku.status())) {
                    throw new BusinessException("SKU_OFF_SALE", "商品已下架: SKU " + entry.getKey());
                }
                if (sku.price() == null || sku.price().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("SKU_PRICE_INVALID", "商品价格异常: SKU " + entry.getKey());
                }
                total = total.add(sku.price().multiply(BigDecimal.valueOf(entry.getValue())));
            }
            return total;
        }
        BigDecimal amount = request.orderAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "请提供商品明细或有效的购物篮金额");
        }
        return amount;
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    @Override
    public CalculateDiscountResult previewDiscount(Long promotionId, BigDecimal orderAmount) {
        TieredPromotion promotion = promotionMapper.selectById(promotionId);
        if (promotion == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
        }
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new CalculateDiscountResult(null, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        TieredPromotionRule rule = bestRule(promotionId, orderAmount);
        if (rule == null) {
            return new CalculateDiscountResult(null, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        return new CalculateDiscountResult(rule.getId(), rule.getMinAmount(),
                rule.getDiscountAmount(), true);
    }

    /** 取活动内满足门槛的最高阶梯规则（无则 null）。 */
    private TieredPromotionRule bestRule(Long promotionId, BigDecimal orderAmount) {
        List<TieredPromotionRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<TieredPromotionRule>()
                        .eq(TieredPromotionRule::getPromotionId, promotionId)
                        .le(TieredPromotionRule::getMinAmount, orderAmount)
                        .orderByDesc(TieredPromotionRule::getMinAmount));
        return rules.isEmpty() ? null : rules.getFirst();
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

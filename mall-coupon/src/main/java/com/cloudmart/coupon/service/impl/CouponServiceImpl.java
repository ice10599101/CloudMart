package com.cloudmart.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.CreateCouponTemplateRequest;
import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.entity.UserCoupon;
import com.cloudmart.coupon.repository.CouponTemplateMapper;
import com.cloudmart.coupon.repository.UserCouponMapper;
import com.cloudmart.coupon.service.CouponService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String CLAIM_LOCK_KEY_PREFIX = "lock:coupon:claim:";
    private static final Duration CLAIM_LOCK_WAIT = Duration.ofSeconds(3);
    private static final Duration CLAIM_LOCK_LEASE = Duration.ofSeconds(10);

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponTemplateConverter couponTemplateConverter;
    private final UserCouponConverter userCouponConverter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public CouponTemplateDTO createTemplate(CreateCouponTemplateRequest request) {
        validateCreateTemplateRequest(request);

        CouponTemplate template = new CouponTemplate();
        template.setName(request.name());
        template.setType(request.type());
        template.setThresholdAmount(request.thresholdAmount());
        template.setDiscountAmount(request.discountAmount());
        template.setDiscountRate(request.discountRate());
        template.setTotalQuantity(request.totalQuantity());
        template.setRemainingQuantity(request.totalQuantity());
        template.setPerUserLimit(request.perUserLimit());
        template.setValidityType(request.validityType());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setValidDays(request.validDays());
        template.setStatus("ENABLED");

        couponTemplateMapper.insert(template);

        redisTemplate.opsForValue().set(
                COUPON_STOCK_KEY_PREFIX + template.getId(),
                String.valueOf(template.getRemainingQuantity()),
                Duration.ofDays(365)
        );

        return couponTemplateConverter.toDTO(template);
    }

    @Override
    public CouponTemplateDTO getTemplateById(Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        return couponTemplateConverter.toDTO(template);
    }

    @Override
    public List<CouponTemplateDTO> listTemplates(String type, String status, int page, int size) {
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<CouponTemplate>()
                .eq(type != null && !type.isEmpty(), CouponTemplate::getType, type)
                .eq(status != null && !status.isEmpty(), CouponTemplate::getStatus, status)
                .orderByDesc(CouponTemplate::getCreatedAt);

        Page<CouponTemplate> templatePage = couponTemplateMapper.selectPage(new Page<CouponTemplate>(page, size), wrapper);
        return couponTemplateConverter.toDTOList(templatePage.getRecords());
    }

    @Override
    public long countTemplates(String type, String status) {
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<CouponTemplate>()
                .eq(type != null && !type.isEmpty(), CouponTemplate::getType, type)
                .eq(status != null && !status.isEmpty(), CouponTemplate::getStatus, status);
        return couponTemplateMapper.selectCount(wrapper);
    }

    @Override
    @Transactional
    public CouponTemplateDTO disableTemplate(Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        if (!"ENABLED".equals(template.getStatus())) {
            throw new BusinessException("TEMPLATE_STATUS_ERROR", "当前模板状态不允许禁用");
        }

        LambdaUpdateWrapper<CouponTemplate> updateWrapper = new LambdaUpdateWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, id)
                .eq(CouponTemplate::getStatus, "ENABLED")
                .set(CouponTemplate::getStatus, "DISABLED");
        int updated = couponTemplateMapper.update(null, updateWrapper);
        if (updated == 0) {
            throw new BusinessException("TEMPLATE_STATUS_ERROR", "模板状态已变更，请刷新重试");
        }

        CouponTemplate updatedTemplate = couponTemplateMapper.selectById(id);
        return couponTemplateConverter.toDTO(updatedTemplate);
    }

    @Override
    @Transactional
    public CouponTemplateDTO enableTemplate(Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        if (!"DISABLED".equals(template.getStatus())) {
            throw new BusinessException("TEMPLATE_STATUS_ERROR", "当前模板状态不允许启用");
        }

        LambdaUpdateWrapper<CouponTemplate> updateWrapper = new LambdaUpdateWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, id)
                .eq(CouponTemplate::getStatus, "DISABLED")
                .set(CouponTemplate::getStatus, "ENABLED");
        int updated = couponTemplateMapper.update(null, updateWrapper);
        if (updated == 0) {
            throw new BusinessException("TEMPLATE_STATUS_ERROR", "模板状态已变更，请刷新重试");
        }

        redisTemplate.opsForValue().set(
                COUPON_STOCK_KEY_PREFIX + id,
                String.valueOf(template.getRemainingQuantity()),
                Duration.ofDays(365)
        );

        CouponTemplate updatedTemplate = couponTemplateMapper.selectById(id);
        return couponTemplateConverter.toDTO(updatedTemplate);
    }

    @Override
    @Transactional
    @SentinelResource(value = "claimCoupon", fallback = "claimCouponFallback")
    public UserCouponDTO claimCoupon(Long userId, Long templateId) {
        String lockKey = CLAIM_LOCK_KEY_PREFIX + userId + ":" + templateId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired;
        try {
            acquired = lock.tryLock(CLAIM_LOCK_WAIT.toSeconds(), CLAIM_LOCK_LEASE.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("CLAIM_INTERRUPTED", "领取优惠券被中断");
        }

        if (!acquired) {
            throw new BusinessException("CLAIM_TOO_FREQUENT", "操作过于频繁，请稍后重试");
        }

        try {
            return doClaimCoupon(userId, templateId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private UserCouponDTO doClaimCoupon(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        if (!"ENABLED".equals(template.getStatus())) {
            throw new BusinessException("TEMPLATE_DISABLED", "优惠券模板已禁用");
        }

        if ("FIXED_DATE".equals(template.getValidityType())) {
            if (template.getEndTime() != null && template.getEndTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException("TEMPLATE_EXPIRED", "优惠券模板已过期");
            }
        }

        String currentStock = redisTemplate.opsForValue().get(COUPON_STOCK_KEY_PREFIX + templateId);
        if (currentStock == null) {
            redisTemplate.opsForValue().set(
                    COUPON_STOCK_KEY_PREFIX + templateId,
                    String.valueOf(template.getRemainingQuantity()),
                    Duration.ofDays(365)
            );
            currentStock = String.valueOf(template.getRemainingQuantity());
        }
        if (Long.parseLong(currentStock) <= 0) {
            throw new BusinessException("STOCK_INSUFFICIENT", "优惠券已领完");
        }

        Long claimedCount = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getTemplateId, templateId)
        );
        if (claimedCount >= template.getPerUserLimit()) {
            throw new BusinessException("CLAIM_LIMIT_EXCEEDED", "已达到领取上限");
        }

        Long remainingAfterDecrement = redisTemplate.opsForValue().decrement(COUPON_STOCK_KEY_PREFIX + templateId);
        if (remainingAfterDecrement == null || remainingAfterDecrement < 0) {
            redisTemplate.opsForValue().increment(COUPON_STOCK_KEY_PREFIX + templateId);
            throw new BusinessException("STOCK_INSUFFICIENT", "优惠券已领完");
        }

        LambdaUpdateWrapper<CouponTemplate> stockUpdate = new LambdaUpdateWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, templateId)
                .gt(CouponTemplate::getRemainingQuantity, 0)
                .setSql("remaining_quantity = remaining_quantity - 1");
        int dbUpdated = couponTemplateMapper.update(null, stockUpdate);
        if (dbUpdated == 0) {
            redisTemplate.opsForValue().increment(COUPON_STOCK_KEY_PREFIX + templateId);
            throw new BusinessException("STOCK_INSUFFICIENT", "优惠券已领完");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setTemplateId(templateId);
        userCoupon.setStatus("UNUSED");
        userCoupon.setReceivedAt(LocalDateTime.now());

        if ("FIXED_DATE".equals(template.getValidityType())) {
            userCoupon.setExpiredAt(template.getEndTime());
        } else if ("FIXED_DAYS".equals(template.getValidityType()) && template.getValidDays() != null) {
            userCoupon.setExpiredAt(LocalDateTime.now().plusDays(template.getValidDays()));
        }

        userCouponMapper.insert(userCoupon);

        CouponTemplate refreshedTemplate = couponTemplateMapper.selectById(templateId);
        return userCouponConverter.toDTO(userCoupon, refreshedTemplate);
    }

    @Override
    public List<UserCouponDTO> listUserCoupons(Long userId, String status, int page, int size) {
        if ("UNUSED".equals(status) || status == null) {
            expireOutdatedCoupons(userId);
        }

        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(status != null && !status.isEmpty(), UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getCreatedAt);

        Page<UserCoupon> couponPage = userCouponMapper.selectPage(new Page<UserCoupon>(page, size), wrapper);

        List<Long> templateIds = couponPage.getRecords().stream()
                .map(UserCoupon::getTemplateId)
                .distinct()
                .toList();
        Map<Long, CouponTemplate> templateMap = templateIds.isEmpty() ? Map.of() :
                couponTemplateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(CouponTemplate::getId, t -> t));

        return couponPage.getRecords().stream()
                .map(uc -> userCouponConverter.toDTO(uc, templateMap.get(uc.getTemplateId())))
                .toList();
    }

    @Override
    public long countUserCoupons(Long userId, String status) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(status != null && !status.isEmpty(), UserCoupon::getStatus, status);
        return userCouponMapper.selectCount(wrapper);
    }

    @Override
    public UserCouponDTO getUserCouponById(Long id) {
        UserCoupon userCoupon = userCouponMapper.selectById(id);
        if (userCoupon == null) {
            throw new BusinessException("USER_COUPON_NOT_FOUND", "用户优惠券不存在");
        }
        CouponTemplate template = couponTemplateMapper.selectById(userCoupon.getTemplateId());
        return userCouponConverter.toDTO(userCoupon, template);
    }

    @Override
    @Transactional
    @SentinelResource(value = "useCoupon", fallback = "useCouponFallback")
    public void useCoupon(Long userCouponId, Long orderId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BusinessException("USER_COUPON_NOT_FOUND", "用户优惠券不存在");
        }
        if (!"UNUSED".equals(userCoupon.getStatus())) {
            throw new BusinessException("COUPON_STATUS_ERROR", "优惠券状态不允许使用");
        }
        if (userCoupon.getExpiredAt() != null && userCoupon.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("COUPON_EXPIRED", "优惠券已过期");
        }

        int updated = userCouponMapper.updateStatusIfMatch(
                userCouponId, "UNUSED", "USED", orderId, LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException("COUPON_STATUS_ERROR", "优惠券状态已变更，请刷新重试");
        }
    }

    @Override
    @Transactional
    public void returnCoupon(Long userCouponId, Long orderId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BusinessException("USER_COUPON_NOT_FOUND", "用户优惠券不存在");
        }
        if (!"USED".equals(userCoupon.getStatus())) {
            throw new BusinessException("COUPON_STATUS_ERROR", "优惠券状态不允许退还");
        }
        if (!orderId.equals(userCoupon.getOrderId())) {
            throw new BusinessException("ORDER_MISMATCH", "订单ID不匹配");
        }

        int updated = userCouponMapper.returnCouponIfMatch(
                userCouponId, "USED", "UNUSED", orderId);
        if (updated == 0) {
            throw new BusinessException("COUPON_STATUS_ERROR", "优惠券状态已变更，请刷新重试");
        }
    }

    @Override
    @Transactional
    public int expireBatch() {
        int count = userCouponMapper.batchExpireUnused();
        if (count > 0) {
            log.info("批量过期优惠券完成, count={}", count);
        }
        return count;
    }

    private void validateCreateTemplateRequest(CreateCouponTemplateRequest request) {
        if ("AMOUNT_OFF".equals(request.type())) {
            if (request.discountAmount() == null) {
                throw new BusinessException("VALIDATION_ERROR", "满减券必须指定优惠金额");
            }
        } else if ("PERCENT_OFF".equals(request.type())) {
            if (request.discountRate() == null) {
                throw new BusinessException("VALIDATION_ERROR", "折扣券必须指定折扣率");
            }
        } else {
            throw new BusinessException("VALIDATION_ERROR", "不支持的优惠券类型: " + request.type());
        }

        if ("FIXED_DATE".equals(request.validityType())) {
            if (request.startTime() == null || request.endTime() == null) {
                throw new BusinessException("VALIDATION_ERROR", "固定时间段有效期必须指定开始和结束时间");
            }
            if (request.endTime().isBefore(request.startTime())) {
                throw new BusinessException("VALIDATION_ERROR", "结束时间不能早于开始时间");
            }
        } else if ("FIXED_DAYS".equals(request.validityType())) {
            if (request.validDays() == null) {
                throw new BusinessException("VALIDATION_ERROR", "固定天数有效期必须指定有效天数");
            }
        } else {
            throw new BusinessException("VALIDATION_ERROR", "不支持的有效期类型: " + request.validityType());
        }
    }

    private void expireOutdatedCoupons(Long userId) {
        List<UserCoupon> unusedCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, "UNUSED")
                        .isNotNull(UserCoupon::getExpiredAt)
                        .lt(UserCoupon::getExpiredAt, LocalDateTime.now())
        );

        for (UserCoupon coupon : unusedCoupons) {
            int updated = userCouponMapper.expireIfMatch(coupon.getId(), "UNUSED", "EXPIRED");
            if (updated > 0) {
                log.info("优惠券已过期, userCouponId={}, userId={}", coupon.getId(), userId);
            }
        }
    }

    public UserCouponDTO claimCouponFallback(Long userId, Long templateId, Throwable throwable) {
        log.warn("claimCoupon fallback triggered, userId={}, templateId={}: {}", userId, templateId, throwable.getMessage());
        return null;
    }

    public void useCouponFallback(Long userCouponId, Long orderId, Throwable throwable) {
        log.warn("useCoupon fallback triggered, userCouponId={}, orderId={}: {}", userCouponId, orderId, throwable.getMessage());
    }
}

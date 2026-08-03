package com.cloudmart.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.entity.ExchangeCode;
import com.cloudmart.coupon.repository.CouponTemplateMapper;
import com.cloudmart.coupon.repository.ExchangeCodeMapper;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.util.CodeGenerator;
import com.cloudmart.coupon.vo.ExchangeCodeVO;
import com.cloudmart.coupon.vo.ExchangeCodeVO.BatchGenerateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 兑换码服务实现
 * <p>
 * 核心设计：
 * <ul>
 *   <li>生成：Redis INCR 原子递增序列 → CodeGenerator 生成码 → 批量入库</li>
 *   <li>兑换：BitMap SETBIT 原子防重（快路径）+ DB CAS 状态流转（可靠路径）</li>
 *   <li>补偿：兑换后续环节失败时，手动回滚 DB 状态与 BitMap，避免用户被锁死</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCodeServiceImpl implements ExchangeCodeService {

    /** 兑换码序列号 Redis Key 前缀，配合模板ID使用 */
    private static final String EXCHANGE_SEQ_KEY_PREFIX = "exchange:seq:";

    /** 兑换防重 BitMap Key 前缀，配合模板ID使用，bit 位定位用序列号 */
    private static final String EXCHANGE_BITMAP_KEY_PREFIX = "exchange:bitmap:";

    /** Redis Key TTL，与优惠券最长有效期对齐（365天） */
    private static final Duration KEY_TTL = Duration.ofDays(365);

    private final ExchangeCodeMapper exchangeCodeMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponService couponService;
    private final CodeGenerator codeGenerator;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public BatchGenerateResult generateBatch(Long templateId, int quantity) {
        validateTemplate(templateId);

        String seqKey = EXCHANGE_SEQ_KEY_PREFIX + templateId;
        String batchNo = generateBatchNo();

        List<ExchangeCode> entities = new ArrayList<>(quantity);
        List<String> codeStrings = new ArrayList<>(quantity);

        for (int i = 0; i < quantity; i++) {
            Long seq = redisTemplate.opsForValue().increment(seqKey);
            if (seq == null || seq <= 0) {
                throw new BusinessException("SEQ_GENERATION_FAILED", "序列号生成失败");
            }
            // 首次生成时设置 TTL，避免 Key 永不过期
            if (seq == 1L) {
                redisTemplate.expire(seqKey, KEY_TTL);
            }

            String code = codeGenerator.generate(seq);
            ExchangeCode entity = new ExchangeCode();
            entity.setCode(code);
            entity.setTemplateId(templateId);
            entity.setSerialNumber(seq.intValue());
            entity.setStatus("UNUSED");
            entity.setExchangeBatch(batchNo);
            entities.add(entity);
            codeStrings.add(code);
        }

        entities.forEach(exchangeCodeMapper::insert);

        log.info("兑换码批量生成完成, templateId={}, batchNo={}, count={}", templateId, batchNo, quantity);
        return new BatchGenerateResult(batchNo, codeStrings, quantity, templateId);
    }

    @Override
    public Long exchange(Long userId, String code) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "用户ID非法");
        }
        if (!codeGenerator.validate(code)) {
            throw new BusinessException("INVALID_CODE_FORMAT", "兑换码格式非法");
        }

        ExchangeCode exchangeCode = exchangeCodeMapper.selectOne(
                new LambdaQueryWrapper<ExchangeCode>().eq(ExchangeCode::getCode, code));
        if (exchangeCode == null) {
            throw new BusinessException("EXCHANGE_CODE_NOT_FOUND", "兑换码不存在");
        }
        if ("DISABLED".equals(exchangeCode.getStatus())) {
            throw new BusinessException("EXCHANGE_CODE_DISABLED", "兑换码已作废");
        }
        if ("EXCHANGED".equals(exchangeCode.getStatus())) {
            throw new BusinessException("EXCHANGE_CODE_ALREADY_USED", "兑换码已被使用");
        }

        Long templateId = exchangeCode.getTemplateId();
        Integer serialNumber = exchangeCode.getSerialNumber();
        String bitmapKey = EXCHANGE_BITMAP_KEY_PREFIX + templateId;

        // BitMap 原子 SETBIT：返回旧值，true 表示已被兑换（快路径防重）
        Boolean previousBit = redisTemplate.opsForValue().setBit(bitmapKey, serialNumber, true);
        if (Boolean.TRUE.equals(previousBit)) {
            throw new BusinessException("EXCHANGE_CODE_ALREADY_USED", "兑换码已被使用");
        }
        ensureBitmapTtl(bitmapKey);

        try {
            // DB CAS：可靠的状态流转，兜底 BitMap 丢失场景
            int updated = exchangeCodeMapper.markExchangedIfUnused(code, userId, LocalDateTime.now());
            if (updated == 0) {
                clearBitmapSafely(bitmapKey, serialNumber);
                throw new BusinessException("EXCHANGE_CODE_ALREADY_USED", "兑换码已被使用");
            }

            // 调用优惠券领取，失败则触发补偿
            UserCouponDTO userCoupon = couponService.claimCoupon(userId, templateId);
            log.info("兑换码兑换成功, code={}, userId={}, userCouponId={}", code, userId, userCoupon.id());
            return userCoupon.id();
        } catch (BusinessException e) {
            // 业务异常（如库存不足）需要回滚已做的状态变更
            if ("EXCHANGE_CODE_ALREADY_USED".equals(e.getCode())) {
                throw e;
            }
            rollbackExchangeState(code, bitmapKey, serialNumber);
            throw e;
        } catch (Exception e) {
            rollbackExchangeState(code, bitmapKey, serialNumber);
            throw new BusinessException("EXCHANGE_FAILED", "兑换失败，请稍后重试", e);
        }
    }

    @Override
    public ExchangeCodeVO getByCode(String code) {
        ExchangeCode exchangeCode = exchangeCodeMapper.selectOne(
                new LambdaQueryWrapper<ExchangeCode>().eq(ExchangeCode::getCode, code));
        if (exchangeCode == null) {
            throw new BusinessException("EXCHANGE_CODE_NOT_FOUND", "兑换码不存在");
        }
        return toVO(exchangeCode);
    }

    @Override
    public List<ExchangeCodeVO> listByTemplate(Long templateId, String status, int page, int size) {
        LambdaQueryWrapper<ExchangeCode> wrapper = new LambdaQueryWrapper<ExchangeCode>()
                .eq(ExchangeCode::getTemplateId, templateId)
                .eq(status != null && !status.isEmpty(), ExchangeCode::getStatus, status)
                .orderByDesc(ExchangeCode::getCreatedAt);
        Page<ExchangeCode> result = exchangeCodeMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords().stream().map(this::toVO).toList();
    }

    @Override
    public long countByTemplate(Long templateId, String status) {
        LambdaQueryWrapper<ExchangeCode> wrapper = new LambdaQueryWrapper<ExchangeCode>()
                .eq(ExchangeCode::getTemplateId, templateId)
                .eq(status != null && !status.isEmpty(), ExchangeCode::getStatus, status);
        return exchangeCodeMapper.selectCount(wrapper);
    }

    @Override
    @Transactional
    public void disable(String code) {
        int updated = exchangeCodeMapper.disableIfUnused(code);
        if (updated == 0) {
            ExchangeCode exchangeCode = exchangeCodeMapper.selectOne(
                    new LambdaQueryWrapper<ExchangeCode>().eq(ExchangeCode::getCode, code));
            if (exchangeCode == null) {
                throw new BusinessException("EXCHANGE_CODE_NOT_FOUND", "兑换码不存在");
            }
            throw new BusinessException("EXCHANGE_CODE_STATUS_ERROR",
                    "兑换码当前状态不允许作废: " + exchangeCode.getStatus());
        }
        log.info("兑换码已作废, code={}", code);
    }

    private void validateTemplate(Long templateId) {
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        if (!"ENABLED".equals(template.getStatus())) {
            throw new BusinessException("TEMPLATE_DISABLED", "优惠券模板已禁用");
        }
    }

    private String generateBatchNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return timestamp + random;
    }

    /**
     * 确保 BitMap Key 设置 TTL，避免永不过期的业务 Key
     */
    private void ensureBitmapTtl(String bitmapKey) {
        Long ttl = redisTemplate.getExpire(bitmapKey);
        if (ttl == null || ttl < 0) {
            redisTemplate.expire(bitmapKey, KEY_TTL);
        }
    }

    /**
     * 安全清除 BitMap 位（失败仅记录日志，不影响主流程）
     */
    private void clearBitmapSafely(String bitmapKey, Integer serialNumber) {
        try {
            redisTemplate.opsForValue().setBit(bitmapKey, serialNumber, false);
        } catch (Exception ex) {
            log.error("清除兑换BitMap失败, key={}, bit={}", bitmapKey, serialNumber, ex);
        }
    }

    /**
     * 兑换后续环节失败的补偿回滚：恢复 DB 状态 + 清除 BitMap
     */
    private void rollbackExchangeState(String code, String bitmapKey, Integer serialNumber) {
        try {
            exchangeCodeMapper.rollbackExchanged(code);
            redisTemplate.opsForValue().setBit(bitmapKey, serialNumber, false);
            log.warn("兑换回滚成功, code={}", code);
        } catch (Exception ex) {
            log.error("兑换回滚失败, code={}, 需人工排查 BitMap 与 DB 一致性", code, ex);
        }
    }

    private ExchangeCodeVO toVO(ExchangeCode entity) {
        return new ExchangeCodeVO(
                entity.getId(),
                entity.getCode(),
                entity.getTemplateId(),
                entity.getStatus(),
                entity.getUserId(),
                entity.getExchangedAt(),
                entity.getCreatedAt());
    }
}

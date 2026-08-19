package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.GrantConsentRequest;
import com.cloudmart.wish.entity.WishConsent;
import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.repository.WishConsentMapper;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.vo.ConsentRecordVO;
import com.cloudmart.wish.vo.ConsentStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * 用户同意记录服务实现（文档 1.2 节 ⑳ / 34.2 / 39.8）。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>幂等：{@code uk_consent_unique(user_id, consent_type, version, action)} 冲突时
 *       查询并返回已有记录（重复提交无报错，验收标准 2677 行）</li>
 *   <li>consentTextHash：客户端可提交协议文本 SHA-256（防篡改留痕）；
 *       未提交时服务端按 {@code type:version} 生成确定性哈希占位
 *       （协议文本管理模块上线后切换为强制校验）</li>
 *   <li>有效性判定：同 (userId, consentType) 最新一条记录 action=GRANT 即有效</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentServiceImpl implements ConsentService {

    private final WishConsentMapper wishConsentMapper;

    @Override
    public ConsentRecordVO recordConsent(Long userId, GrantConsentRequest request,
                                         String ip, String userAgent) {
        ConsentAction action = request.safeAction();
        WishConsent existing = findByUniqueKey(userId, request.consentType(), request.version(), action);
        if (existing != null) {
            return toRecordVO(existing);
        }

        WishConsent consent = new WishConsent();
        consent.setUserId(userId);
        consent.setConsentType(request.consentType());
        consent.setVersion(request.version());
        consent.setConsentTextHash(resolveTextHash(request));
        consent.setAction(action);
        consent.setIp(truncate(ip, 45));
        consent.setUserAgent(truncate(userAgent, 255));
        consent.setCreatedAt(LocalDateTime.now());
        try {
            wishConsentMapper.insert(consent);
        } catch (DuplicateKeyException ex) {
            // 并发重复提交：读取已有记录幂等返回
            log.info("同意记录并发重复，幂等返回已有记录, userId={}, type={}, version={}, action={}",
                    userId, request.consentType(), request.version(), action);
            WishConsent concurrent = findByUniqueKey(userId, request.consentType(),
                    request.version(), action);
            if (concurrent != null) {
                return toRecordVO(concurrent);
            }
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "同意记录提交失败，请重试");
        }
        return toRecordVO(consent);
    }

    @Override
    public ConsentStatusVO getConsentStatus(Long userId, ConsentType consentType) {
        WishConsent latest = findLatest(userId, consentType);
        if (latest == null) {
            return new ConsentStatusVO(consentType, false, null, null, null);
        }
        return new ConsentStatusVO(consentType, latest.getAction() == ConsentAction.GRANT,
                latest.getVersion(), latest.getAction(), latest.getCreatedAt());
    }

    @Override
    public boolean hasGrantedAiDataProcessing(Long userId) {
        return getConsentStatus(userId, ConsentType.AI_DATA_PROCESSING).granted();
    }

    private WishConsent findByUniqueKey(Long userId, ConsentType type, String version, ConsentAction action) {
        return wishConsentMapper.selectOne(new LambdaQueryWrapper<WishConsent>()
                .eq(WishConsent::getUserId, userId)
                .eq(WishConsent::getConsentType, type)
                .eq(WishConsent::getVersion, version)
                .eq(WishConsent::getAction, action)
                .last("LIMIT 1"));
    }

    private WishConsent findLatest(Long userId, ConsentType type) {
        return wishConsentMapper.selectOne(new LambdaQueryWrapper<WishConsent>()
                .eq(WishConsent::getUserId, userId)
                .eq(WishConsent::getConsentType, type)
                .orderByDesc(WishConsent::getId)
                .last("LIMIT 1"));
    }

    private String resolveTextHash(GrantConsentRequest request) {
        if (request.consentTextHash() != null && !request.consentTextHash().isBlank()) {
            return request.consentTextHash().toLowerCase();
        }
        return sha256(request.consentType() + ":" + request.version());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 为 JVM 必选算法，理论上不可达
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private ConsentRecordVO toRecordVO(WishConsent consent) {
        return new ConsentRecordVO(consent.getId(), consent.getConsentType(),
                consent.getVersion(), consent.getAction(), consent.getCreatedAt());
    }
}

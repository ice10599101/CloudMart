package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.GrantConsentRequest;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.vo.ConsentRecordVO;
import com.cloudmart.wish.vo.ConsentStatusVO;

/**
 * 用户同意记录服务（文档 1.2 节 ⑳ / 34.2 合规留痕 / 39.8 AI 安全）。
 */
public interface ConsentService {

    /**
     * 提交同意/撤回记录。
     *
     * <p>幂等：相同 (userId, consentType, version, action) 重复提交返回已有记录，不报错。
     * 唯一约束 {@code uk_consent_unique} 兜底防并发重复。</p>
     *
     * @param userId    用户 ID
     * @param request   请求体
     * @param ip        操作 IP（可空）
     * @param userAgent User-Agent（可空）
     * @return 同意记录
     */
    ConsentRecordVO recordConsent(Long userId, GrantConsentRequest request, String ip, String userAgent);

    /**
     * 查询指定类型的当前同意状态（最新一条记录判定）。
     */
    ConsentStatusVO getConsentStatus(Long userId, ConsentType consentType);

    /**
     * 是否已同意 AI 数据处理协议（文档 39.8：首次 AI 能力使用前必须取得）。
     */
    boolean hasGrantedAiDataProcessing(Long userId);
}

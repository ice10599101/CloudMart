package com.cloudmart.risk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.risk.entity.BlacklistEntry;

import java.time.LocalDateTime;

public interface BlacklistService {

    BlacklistEntry addToBlacklist(String targetType, String targetValue, String reason, LocalDateTime expiredAt);

    void removeFromBlacklist(String targetType, String targetValue);

    boolean isBlacklisted(String targetType, String targetValue);

    IPage<BlacklistEntry> listBlacklist(String targetType, int page, int pageSize);
}

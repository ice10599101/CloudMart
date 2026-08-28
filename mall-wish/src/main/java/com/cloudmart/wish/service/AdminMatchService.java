package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.MatchConfig;
import com.cloudmart.wish.vo.MatchGroupDetailVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端同愿匹配服务（Sprint 2.6 管理后台：小组管理 + 匹配算法配置）。
 */
public interface AdminMatchService {

    /** 全量小组列表（含 CLOSED，活跃度监控口径） */
    List<AdminMatchGroupRow> listGroups(String status, String keyword);

    /** 强制解散异常小组（成员收到通知；与组长解散同一链路） */
    void forceDissolve(Long groupId, Long adminUserId);

    /** 匹配算法配置列表 */
    List<MatchConfig> listConfigs();

    /** 更新匹配算法配置（实时生效） */
    MatchConfig updateConfig(String configKey, String configValue, Long adminUserId);

    /**
     * 管理端小组行。
     *
     * @param lastActiveAt 最近一次组内成员活跃时间（活跃度监控；null=全部成员从未活跃）
     */
    record AdminMatchGroupRow(
            Long groupId,
            String keyword,
            Integer memberCount,
            Integer maxMembers,
            String status,
            String cityCode,
            Long leaderId,
            String leaderNickname,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt) {
    }
}

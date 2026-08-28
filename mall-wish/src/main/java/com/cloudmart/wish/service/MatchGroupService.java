package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateMatchGroupRequest;
import com.cloudmart.wish.dto.JoinGroupRequest;
import com.cloudmart.wish.dto.MatchRecommendQuery;
import com.cloudmart.wish.vo.MatchGroupCreatedVO;
import com.cloudmart.wish.vo.MatchGroupDetailVO;
import com.cloudmart.wish.vo.MatchGroupVO;

import java.util.List;

/**
 * 同愿匹配 + 监督小队服务（Sprint 2.6，文档 2.8/十章）。
 *
 * <p>错误码：400 WISH_VALIDATION_ERROR / 403 WISH_KICKED_COOLDOWN
 * （被踢 24h 冷却）/ WISH_GROUP_LEADER_REQUIRED（非组长踢人/解散）/
 * 404 WISH_GROUP_NOT_FOUND / 409 WISH_GROUP_FULL / WISH_ALREADY_MEMBER /
 * WISH_GROUP_KEYWORD_DUPLICATED / 429 WISH_RATE_LIMITED（建组日限频）。</p>
 */
public interface MatchGroupService {

    /** 建组（创建者为 LEADER；每用户每日建组数受限频约束） */
    MatchGroupCreatedVO createGroup(Long userId, CreateMatchGroupRequest request);

    /**
     * 匹配推荐（OPEN 组按相似度降序；排除本人已加入的组；
     * keyword/city 皆空时基于用户心愿标签推荐，冷启动按活跃度兜底）。
     *
     * @param userId 当前用户（可空=匿名浏览，仅按参数匹配）
     */
    MatchGroupVO.MatchPage recommendGroups(Long userId, MatchRecommendQuery query);

    /** 加入小组（CAS 占位防并发超卖；被踢冷却/重复加入/满员校验） */
    void joinGroup(Long userId, Long groupId, JoinGroupRequest request);

    /**
     * 退出或踢出（self=退出；target≠self 需组长权限）。
     * 组长退出自动转让给最早加入的 MEMBER，无成员则组关闭（文档 2.8）。
     * 被踢者进入 24h 同关键词冷却。
     */
    void leaveOrKickMember(Long userId, Long groupId, Long targetUserId);

    /** 解散小组（仅组长；成员收到通知） */
    void dissolveGroup(Long userId, Long groupId);

    /**
     * 互相提醒（提醒指定组员，或全部 idle 超过 remind_idle_days 的组员；
     * 发送者每日提醒条数受限频约束）。
     *
     * @param targetUserId 目标组员（可空=提醒全部 idle 组员）
     */
    void remindMembers(Long userId, Long groupId, Long targetUserId);

    /** 我的小组（含 ACTIVE 成员列表与活跃度） */
    List<MatchGroupDetailVO> listMyGroups(Long userId);

    /** 小组详情（成员仅暴露昵称/头像/活跃度） */
    MatchGroupDetailVO getGroupDetail(Long userId, Long groupId);
}

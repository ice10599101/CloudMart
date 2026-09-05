package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.ActivityParticipant;
import com.cloudmart.wish.entity.ActivityRewardLog;
import com.cloudmart.wish.entity.CommunityActivity;
import com.cloudmart.wish.vo.ActivityBoardVO;

import java.util.List;

/**
 * 社区活动服务（Sprint 3.5，文档 2.21/3.5）。
 *
 * <p>活动状态机 DRAFT → ACTIVE → ENDED → ARCHIVED（管理端手动流转）；
 * 归档后列表/入口消失，详情页仍可访问；奖励发放幂等（uk 活动×用户×类型，
 * 重复发放返回"已发放"）。</p>
 */
public interface ActivityService {

    // ---------------- 浏览（公开） ----------------

    /**
     * 活动列表（入口展示）：仅 ACTIVE 且在展示期内；type/cityCode 过滤可选；
     * 归档活动不出现在列表（验收）。
     */
    /** 参与者列表（文档 2.21）：JOINED/APPROVED，昵称脱敏为 ID 尾号 */
    java.util.List<java.util.Map<String, Object>> listParticipants(Long activityId, int page, int size);

    List<CommunityActivity> listActivities(String type, String cityCode);

    /** 活动详情（归档后仍可访问——验收） */
    CommunityActivity getActivity(Long activityId);

    /** 活动进度（Redis 原子计数为准） */
    long getProgress(Long activityId);

    // ---------------- 参与（登录） ----------------

    /** 普通参与（进度 Redis INCR + 参与行 JOINED；重复参与幂等不重复计数） */
    void join(Long userId, Long activityId);

    /**
     * 心愿合伙人申请（提交协作心愿 + 技能标签；服务端计算与招募需求的
     * 技能匹配度）。
     */
    void applyPartner(Long userId, Long activityId, Long wishId, List<String> skills);

    /** 招募作者审批（APPROVED 进组 / REJECTED） */
    void reviewApplication(Long userId, Long activityId, Long applicantUserId, boolean approved);

    /** 组队看板：成员列表 + 各自打卡天数/最新成长记录（协作进度共享） */
    List<ActivityBoardVO.MemberBoard> getPartnerBoard(Long activityId, Long viewerId);

    // ---------------- 管理端 ----------------

    /** 活动列表（全状态分页） */
    List<CommunityActivity> listForAdmin(String status, String type, int page, int size);

    /** 创建活动（DRAFT；condition/reward JSON 校验） */
    CommunityActivity create(CommunityActivity activity, Long adminUserId);

    /** 更新活动（仅 DRAFT/ACTIVE 可改；ACTIVE 改后失效相关缓存） */
    CommunityActivity update(Long activityId, CommunityActivity activity, Long adminUserId);

    /** 状态机流转：start（DRAFT→ACTIVE）/ end（ACTIVE→ENDED）/ archive（ENDED→ARCHIVED） */
    CommunityActivity transition(Long activityId, String action, Long adminUserId);

    /** 删除活动（仅 DRAFT 可删） */
    void delete(Long activityId);

    /**
     * 奖励发放（达标用户自动发放：条件达成时对全部参与/组队用户发
     * 星光/徽章；uk 幂等——重复发放跳过，返回"已发放"计数）。
     *
     * @return {eligible, starlightIssued, badgeIssued, skipped}
     */
    RewardIssueStats issueRewards(Long activityId, Long adminUserId);

    /** 奖励发放日志（审计：活动维度倒序） */
    List<ActivityRewardLog> listRewardLogs(Long activityId);

    /**
     * 奖励发放统计。
     */
    record RewardIssueStats(long eligible, long starlightIssued, long badgeIssued, long skipped) {
    }
}

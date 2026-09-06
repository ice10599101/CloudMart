package com.cloudmart.wish.service.impl;

import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.ActivityParticipant;
import com.cloudmart.wish.entity.ActivityRewardLog;
import com.cloudmart.wish.entity.CommunityActivity;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.entity.WishUserBadge;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.ActivityParticipantStatus;
import com.cloudmart.wish.enums.ActivityRewardType;
import com.cloudmart.wish.enums.ActivityStatus;
import com.cloudmart.wish.enums.ActivityType;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.ActivityParticipantMapper;
import com.cloudmart.wish.repository.ActivityRewardLogMapper;
import com.cloudmart.wish.repository.CommunityActivityMapper;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishUserBadgeMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.ActivityService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.ActivityBoardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 社区活动服务实现（Sprint 3.5）。
 *
 * <p>状态机：DRAFT → ACTIVE（start）→ ENDED（end）→ ARCHIVED（archive）；
 * 归档后列表/入口消失（listActivities 仅 ACTIVE），详情仍可访问。</p>
 *
 * <p>进度：Redis INCR activity:progress:{id}（原子计数支撑千人并发），
 * DB progress_counter 为回写镜像；奖励发放 uk(activity,user,type) 幂等
 * （重复发放跳过计入 skipped——验收"重复发放返回已发放"）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityServiceImpl implements ActivityService {

    private static final String PROGRESS_KEY_PREFIX = "activity:progress:";
    private static final int MAX_PAGE_SIZE = 100;

    private final com.cloudmart.wish.repository.ActivityParticipantMapper activityParticipantMapper;
    private final CommunityActivityMapper activityMapper;
    private final ActivityParticipantMapper participantMapper;
    private final ActivityRewardLogMapper rewardLogMapper;
    private final WishMapper wishMapper;
    private final com.cloudmart.wish.repository.WishProgressMapper progressMapper;
    private final WishUserStatMapper userStatMapper;
    private final WishBadgeMapper badgeMapper;
    private final WishUserBadgeMapper userBadgeMapper;
    private final WishGrowthRecordMapper growthRecordMapper;
    private final UserStatService userStatService;
    private final StringRedisTemplate redisTemplate;

    // ---------------- 浏览 ----------------

    @Override
    public java.util.List<Map<String, Object>> listParticipants(Long activityId, int page, int size) {
        final var participants = activityParticipantMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.wish.entity.ActivityParticipant>()
                        .eq(com.cloudmart.wish.entity.ActivityParticipant::getActivityId, activityId)
                        .in(com.cloudmart.wish.entity.ActivityParticipant::getStatus,
                                com.cloudmart.wish.enums.ActivityParticipantStatus.JOINED,
                                com.cloudmart.wish.enums.ActivityParticipantStatus.APPROVED)
                        .orderByAsc(com.cloudmart.wish.entity.ActivityParticipant::getId)
                        .last("LIMIT " + Math.min(Math.max(size, 1), 100) + " OFFSET " + Math.max(page - 1, 0) * size));
        return participants.stream().map(p -> {
            final Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
            final String uid = String.valueOf(p.getUserId());
            row.put("userIdMasked", "用户" + uid.substring(Math.max(0, uid.length() - 4)));
            row.put("role", p.getRole());
            row.put("status", p.getStatus() != null ? p.getStatus().name() : null);
            row.put("matchScore", p.getMatchScore());
            row.put("joinedAt", p.getCreatedAt());
            return row;
        }).toList();
    }

    @Override
    public List<CommunityActivity> listActivities(String type, String cityCode) {
        LambdaQueryWrapper<CommunityActivity> query = new LambdaQueryWrapper<>();
        query.eq(CommunityActivity::getStatus, ActivityStatus.ACTIVE);
        applyTypeAndCity(query, type, cityCode);
        query.orderByDesc(CommunityActivity::getId);
        List<CommunityActivity> activities = activityMapper.selectList(query.last("LIMIT 100"));
        // 进度统一走 getProgress（Redis 优先、DB 兜底）：参与只更新 Redis，
        // 若列表直接读 DB progressCounter 会与详情弹窗出现计数不一致
        activities.forEach(a -> a.setProgressCounter(getProgress(a.getId())));
        return activities;
    }

    @Override
    public CommunityActivity getActivity(Long activityId) {
        CommunityActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    @Override
    public long getProgress(Long activityId) {
        try {
            String value = redisTemplate.opsForValue().get(PROGRESS_KEY_PREFIX + activityId);
            if (value != null) {
                return Long.parseLong(value);
            }
        } catch (DataAccessException | NumberFormatException ex) {
            log.warn("活动进度 Redis 读取失败，回源 DB: {}", ex.getMessage());
        }
        CommunityActivity activity = activityMapper.selectById(activityId);
        return activity == null || activity.getProgressCounter() == null ? 0 : activity.getProgressCounter();
    }

    // ---------------- 参与 ----------------

    @Override
    @Transactional
    public void join(Long userId, Long activityId) {
        CommunityActivity activity = requireActivity(activityId);
        requireJoinable(activity);
        ActivityParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .eq(ActivityParticipant::getUserId, userId)
                .last("LIMIT 1"));
        if (participant != null) {
            return;
        }
        ActivityParticipant insert = new ActivityParticipant();
        insert.setActivityId(activityId);
        insert.setUserId(userId);
        insert.setRole("MEMBER");
        insert.setStatus(ActivityParticipantStatus.JOINED);
        participantMapper.insert(insert);
        incrProgress(activityId);
    }

    @Override
    @Transactional
    public void applyPartner(Long userId, Long activityId, Long wishId, List<String> skills) {
        CommunityActivity activity = requireActivity(activityId);
        if (activity.getType() != ActivityType.WISH_PARTNER) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "该活动不支持合伙人申请");
        }
        requireJoinable(activity);
        if (wishId == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "请选择要协作的心愿");
        }
        if (activityMapper.selectById(activityId) == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "活动不存在");
        }
        // 心愿归属校验（防探测）
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        // 匹配度：申请技能 vs 招募需求技能
        List<String> required = ActivityConditionParser.requiredSkills(activity.getConditionJson());
        int score = ActivityConditionParser.matchScore(required, skills);

        ActivityParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .eq(ActivityParticipant::getUserId, userId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (participant == null) {
            ActivityParticipant insert = new ActivityParticipant();
            insert.setActivityId(activityId);
            insert.setUserId(userId);
            insert.setRole("MEMBER");
            insert.setStatus(ActivityParticipantStatus.PENDING);
            insert.setWishId(wishId);
            insert.setSkills(WishJsonUtils.stringifyList(skills));
            insert.setMatchScore(score);
            insert.setAppliedAt(now);
            participantMapper.insert(insert);
        } else if (participant.getStatus() == ActivityParticipantStatus.JOINED) {
            participant.setStatus(ActivityParticipantStatus.PENDING);
            participant.setWishId(wishId);
            participant.setSkills(WishJsonUtils.stringifyList(skills));
            participant.setMatchScore(score);
            participant.setAppliedAt(now);
            participantMapper.updateById(participant);
        } else {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已提交过申请");
        }
    }

    @Override
    @Transactional
    public void reviewApplication(Long userId, Long activityId, Long applicantUserId, boolean approved) {
        CommunityActivity activity = requireActivity(activityId);
        if (!activity.getCreatedBy().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "仅招募发起人可审批");
        }
        ActivityParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .eq(ActivityParticipant::getUserId, applicantUserId)
                .last("LIMIT 1"));
        if (participant == null || participant.getStatus() != ActivityParticipantStatus.PENDING) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "申请不存在或已审批");
        }
        participant.setStatus(approved ? ActivityParticipantStatus.APPROVED : ActivityParticipantStatus.REJECTED);
        participant.setReviewedAt(LocalDateTime.now(ZoneId.of("UTC")));
        participantMapper.updateById(participant);
        if (approved) {
            incrProgress(activityId);
        }
    }

    @Override
    public List<ActivityBoardVO.MemberBoard> getPartnerBoard(Long activityId, Long viewerId) {
        CommunityActivity activity = requireActivity(activityId);
        if (activity.getType() != ActivityType.WISH_PARTNER) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非合伙人活动");
        }
        // 仅组内可见（LEADER=创建者 或 APPROVED 成员）
        boolean isLeader = activity.getCreatedBy().equals(viewerId);
        if (!isLeader) {
            ActivityParticipant self = participantMapper.selectOne(new LambdaQueryWrapper<ActivityParticipant>()
                    .eq(ActivityParticipant::getActivityId, activityId)
                    .eq(ActivityParticipant::getUserId, viewerId)
                    .last("LIMIT 1"));
            if (self == null || self.getStatus() != ActivityParticipantStatus.APPROVED) {
                throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "仅组内成员可查看看板");
            }
        }
        List<ActivityParticipant> members = participantMapper.selectList(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .in(ActivityParticipant::getStatus,
                        ActivityParticipantStatus.APPROVED, ActivityParticipantStatus.JOINED)
                .orderByAsc(ActivityParticipant::getId));

        List<ActivityBoardVO.MemberBoard> boards = new ArrayList<>();
        for (ActivityParticipant member : members) {
            boards.add(buildMemberBoard(member, activity));
        }
        return boards;
    }

    private ActivityBoardVO.MemberBoard buildMemberBoard(ActivityParticipant member, CommunityActivity activity) {
        WishUserStat stat = userStatMapper.selectById(member.getUserId());
        int checkinDays = stat != null && stat.getTotalCheckinDays() != null ? stat.getTotalCheckinDays() : 0;

        String latestGrowth = null;
        LocalDateTime latestAt = null;
        int percentage = 0;
        String title = null;
        if (member.getWishId() != null) {
            Wish wish = wishMapper.selectById(member.getWishId());
            if (wish != null) {
                title = wish.getTitle();
                var progress = progressMapper.selectOne(
                        new LambdaQueryWrapper<com.cloudmart.wish.entity.WishProgress>()
                                .eq(com.cloudmart.wish.entity.WishProgress::getWishId, wish.getId())
                                .last("LIMIT 1"));
                if (progress != null && progress.getTargetValue() != null && progress.getTargetValue() > 0) {
                    percentage = Math.min(100, Math.round(progress.getCurrentValue() * 100.0f
                            / progress.getTargetValue()));
                }
                var growth = growthRecordMapper.selectList(new LambdaQueryWrapper<WishGrowthRecord>()
                        .eq(WishGrowthRecord::getWishId, wish.getId())
                        .orderByDesc(WishGrowthRecord::getCreatedAt)
                        .last("LIMIT 1"));
                if (!growth.isEmpty()) {
                    latestGrowth = growth.get(0).getContent();
                    latestAt = growth.get(0).getCreatedAt();
                }
            }
        }
        return new ActivityBoardVO.MemberBoard(member.getUserId(), member.getRole(), title,
                percentage, checkinDays, latestGrowth, latestAt);
    }

    // ---------------- 管理端 ----------------

    @Override
    public List<CommunityActivity> listForAdmin(String status, String type, int page, int size) {
        LambdaQueryWrapper<CommunityActivity> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(CommunityActivity::getStatus, ActivityStatus.valueOf(status.trim()));
        }
        if (type != null && !type.isBlank()) {
            query.eq(CommunityActivity::getType, ActivityType.valueOf(type.trim()));
        }
        query.orderByDesc(CommunityActivity::getId);
        return activityMapper.selectList(query
                .last("LIMIT " + Math.min(Math.max(1, size), MAX_PAGE_SIZE)
                        + " OFFSET " + Math.max(0, (page - 1) * size)));
    }

    @Override
    @Transactional
    public CommunityActivity create(CommunityActivity activity, Long adminUserId) {
        validateActivity(activity);
        activity.setCreatedBy(adminUserId);
        activity.setStatus(ActivityStatus.DRAFT);
        if (activity.getProgressCounter() == null) {
            activity.setProgressCounter(0L);
        }
        activityMapper.insert(activity);
        return activity;
    }

    @Override
    @Transactional
    public CommunityActivity update(Long activityId, CommunityActivity activity, Long adminUserId) {
        CommunityActivity existing = requireActivity(activityId);
        if (existing.getStatus() == ActivityStatus.ENDED || existing.getStatus() == ActivityStatus.ARCHIVED) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已结束/归档的活动不可编辑");
        }
        validateActivity(activity);
        CommunityActivity update = new CommunityActivity();
        update.setId(activityId);
        update.setTitle(activity.getTitle());
        update.setDescription(activity.getDescription());
        update.setCoverImage(activity.getCoverImage());
        update.setConditionJson(activity.getConditionJson());
        update.setRewardJson(activity.getRewardJson());
        update.setCityCode(activity.getCityCode());
        update.setValidFrom(activity.getValidFrom());
        update.setValidTo(activity.getValidTo());
        activityMapper.updateById(update);
        // 表无 updated_by 列（审计走 OperLog），管理员身份记入日志
        log.info("活动更新, activityId={}, adminUserId={}", activityId, adminUserId);
        redisTemplate.delete(PROGRESS_KEY_PREFIX + activityId);
        return activityMapper.selectById(activityId);
    }

    @Override
    @Transactional
    public CommunityActivity transition(Long activityId, String action, Long adminUserId) {
        CommunityActivity activity = requireActivity(activityId);
        ActivityStatus next = switch (action) {
            case "start" -> ActivityStatus.ACTIVE;
            case "end" -> ActivityStatus.ENDED;
            case "archive" -> ActivityStatus.ARCHIVED;
            default -> throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法的状态流转: " + action);
        };
        boolean valid = switch (next) {
            case ACTIVE -> activity.getStatus() == ActivityStatus.DRAFT;
            case ENDED -> activity.getStatus() == ActivityStatus.ACTIVE;
            case ARCHIVED -> activity.getStatus() == ActivityStatus.ENDED;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "状态不允许 " + activity.getStatus() + " → " + next);
        }
        activityMapper.update(null, new LambdaUpdateWrapper<CommunityActivity>()
                .set(CommunityActivity::getStatus, next)
                .eq(CommunityActivity::getId, activityId)
                .eq(CommunityActivity::getStatus, activity.getStatus()));
        log.info("活动状态流转, activityId={}, {} → {}, adminUserId={}",
                activityId, activity.getStatus(), next, adminUserId);
        return activityMapper.selectById(activityId);
    }

    @Override
    @Transactional
    public void delete(Long activityId) {
        CommunityActivity activity = requireActivity(activityId);
        if (activity.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "仅筹备中的活动可删除");
        }
        activityMapper.deleteById(activityId);
    }

    @Override
    @Transactional
    public ActivityService.RewardIssueStats issueRewards(Long activityId, Long adminUserId) {
        CommunityActivity activity = requireActivity(activityId);
        if (activity.getStatus() != ActivityStatus.ACTIVE && activity.getStatus() != ActivityStatus.ENDED) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "活动未开始或已归档，不可发奖");
        }
        // 条件判定
        long progress = getProgress(activityId);
        long participantCount = participantMapper.selectCount(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .in(ActivityParticipant::getStatus,
                        ActivityParticipantStatus.JOINED, ActivityParticipantStatus.APPROVED));
        boolean memberFulfilled = hasMemberFulfilled(activityId);
        if (!ActivityConditionParser.isMet(activity.getConditionJson(), progress, participantCount, memberFulfilled)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "活动条件尚未达成");
        }

        // 奖励配置解析
        String rewardJson = activity.getRewardJson();
        int starlight = 0;
        String badgeCode = null;
        if (rewardJson != null && !rewardJson.isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rewardJson);
                starlight = node.path("starlight").asInt(0);
                badgeCode = node.has("badgeCode") && !node.get("badgeCode").isNull()
                        ? node.get("badgeCode").asText() : null;
            } catch (Exception ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "奖励配置 JSON 非法");
            }
        }
        Long badgeId = badgeCode == null ? null : findBadgeIdByCode(badgeCode);

        List<ActivityParticipant> participants = participantMapper.selectList(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .in(ActivityParticipant::getStatus,
                        ActivityParticipantStatus.JOINED, ActivityParticipantStatus.APPROVED));

        long eligible = 0;
        long starlightIssued = 0;
        long badgeIssued = 0;
        long skipped = 0;
        for (ActivityParticipant participant : participants) {
            eligible++;
            boolean anyIssued = false;
            if (starlight > 0) {
                if (tryIssue(activityId, participant.getUserId(), ActivityRewardType.STARLIGHT,
                        starlight, null)) {
                    userStatService.earnStarlight(participant.getUserId(), starlight,
                            ResourceLogSource.ACTIVITY_REWARD, activityId);
                    starlightIssued++;
                    anyIssued = true;
                } else {
                    skipped++;
                    continue;
                }
            }
            if (badgeId != null) {
                if (tryIssue(activityId, participant.getUserId(), ActivityRewardType.BADGE, 1, badgeId)) {
                    WishUserBadge userBadge = new WishUserBadge();
                    userBadge.setUserId(participant.getUserId());
                    userBadge.setBadgeId(badgeId);
                    try {
                        userBadgeMapper.insert(userBadge);
                        badgeIssued++;
                        anyIssued = true;
                    } catch (DuplicateKeyException ex) {
                        // 徽章已持有：日志幂等
                    }
                }
            }
            if (!anyIssued && starlight == 0 && badgeId == null) {
                skipped++;
            }
        }
        log.info("活动奖励发放完成, activityId={}, eligible={}, starlight={}, badge={}, skipped={}, adminUserId={}",
                activityId, eligible, starlightIssued, badgeIssued, skipped, adminUserId);
        return new ActivityService.RewardIssueStats(eligible, starlightIssued, badgeIssued, skipped);
    }

    @Override
    public List<ActivityRewardLog> listRewardLogs(Long activityId) {
        return rewardLogMapper.selectList(new LambdaQueryWrapper<ActivityRewardLog>()
                .eq(ActivityRewardLog::getActivityId, activityId)
                .orderByDesc(ActivityRewardLog::getId)
                .last("LIMIT 200"));
    }

    // ---------------- 工具 ----------------

    /** 奖励日志幂等（uk activity×user×type）：已发放返回 false（计入 skipped） */
    private boolean tryIssue(Long activityId, Long userId, ActivityRewardType type, int amount, Long refId) {
        try {
            ActivityRewardLog logRow = new ActivityRewardLog();
            logRow.setActivityId(activityId);
            logRow.setUserId(userId);
            logRow.setRewardType(type);
            logRow.setAmount(amount);
            logRow.setRefId(refId);
            rewardLogMapper.insert(logRow);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private boolean hasMemberFulfilled(Long activityId) {
        List<ActivityParticipant> members = participantMapper.selectList(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .in(ActivityParticipant::getStatus,
                        ActivityParticipantStatus.JOINED, ActivityParticipantStatus.APPROVED)
                .isNotNull(ActivityParticipant::getWishId));
        for (ActivityParticipant member : members) {
            Wish wish = wishMapper.selectById(member.getWishId());
            if (wish != null && wish.getStatus() == WishStatus.FULFILLED) {
                return true;
            }
        }
        return false;
    }

    private Long findBadgeIdByCode(String badgeCode) {
        WishBadge badge = badgeMapper.selectOne(new LambdaQueryWrapper<WishBadge>()
                .eq(WishBadge::getCode, badgeCode)
                .last("LIMIT 1"));
        if (badge == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "奖励徽章不存在: " + badgeCode);
        }
        return badge.getId();
    }

    private void applyTypeAndCity(LambdaQueryWrapper<CommunityActivity> query, String type, String cityCode) {
        if (type != null && !type.isBlank()) {
            query.eq(CommunityActivity::getType, ActivityType.valueOf(type.trim()));
        }
        if (cityCode != null && !cityCode.isBlank()) {
            query.eq(CommunityActivity::getCityCode, cityCode.trim());
        }
    }

    private void validateActivity(CommunityActivity activity) {
        if (activity.getTitle() == null || activity.getTitle().isBlank()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "活动标题不能为空");
        }
        ActivityConditionParser.validate(activity.getConditionJson());
        if (activity.getType() == ActivityType.CITY
                && (activity.getCityCode() == null || activity.getCityCode().isBlank())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "城市活动必须指定 cityCode");
        }
    }

    private CommunityActivity requireActivity(Long activityId) {
        CommunityActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private void requireJoinable(CommunityActivity activity) {
        if (activity.getStatus() != ActivityStatus.ACTIVE) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "活动不在进行中");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (activity.getValidFrom() != null && now.isBefore(activity.getValidFrom())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "活动尚未开始");
        }
    }

    private void incrProgress(Long activityId) {
        try {
            redisTemplate.opsForValue().increment(PROGRESS_KEY_PREFIX + activityId);
            redisTemplate.expire(PROGRESS_KEY_PREFIX + activityId, Duration.ofDays(30));
        } catch (DataAccessException ex) {
            log.warn("活动进度 INCR 失败（DB 镜像兜底）: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public java.util.Map<String, Integer> autoIssueEligibleRewards() {
        int checked = 0;
        int rewarded = 0;
        int skipped = 0;
        List<CommunityActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<CommunityActivity>()
                        .in(CommunityActivity::getStatus, ActivityStatus.ACTIVE, ActivityStatus.ENDED));
        for (CommunityActivity activity : activities) {
            checked++;
            try {
                long progress = getProgress(activity.getId());
                long participantCount = participantMapper.selectCount(
                        new LambdaQueryWrapper<ActivityParticipant>()
                                .eq(ActivityParticipant::getActivityId, activity.getId())
                                .in(ActivityParticipant::getStatus,
                                        ActivityParticipantStatus.JOINED, ActivityParticipantStatus.APPROVED));
                if (participantCount == 0
                        || !ActivityConditionParser.isMet(activity.getConditionJson(),
                                progress, participantCount, hasMemberFulfilled(activity.getId()))) {
                    continue;
                }
                // SYSTEM 自动发放（adminUserId=0，审计日志可区分于管理员手动触达）
                RewardIssueStats stats = issueRewards(activity.getId(), 0L);
                rewarded += (int) stats.eligible();
                skipped += (int) stats.skipped();
            } catch (Exception ex) {
                // 单活动失败不阻断整批（下一轮扫描按 uk 幂等补偿）
                log.warn("活动自动发奖单活动失败, activityId={}, error={}", activity.getId(), ex.getMessage());
            }
        }
        java.util.Map<String, Integer> stats = new java.util.LinkedHashMap<>();
        stats.put("checked", checked);
        stats.put("rewarded", rewarded);
        stats.put("skipped", skipped);
        log.info("活动自动发奖扫描完成: {}", stats);
        return stats;
    }
}

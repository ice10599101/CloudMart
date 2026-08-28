package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateMatchGroupRequest;
import com.cloudmart.wish.dto.JoinGroupRequest;
import com.cloudmart.wish.dto.MatchRecommendQuery;
import com.cloudmart.wish.entity.MatchGroup;
import com.cloudmart.wish.entity.MatchMember;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.MatchGroupStatus;
import com.cloudmart.wish.enums.MatchMemberRole;
import com.cloudmart.wish.enums.MatchMemberStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.mq.MatchEventProducer;
import com.cloudmart.wish.repository.MatchGroupMapper;
import com.cloudmart.wish.repository.MatchMemberMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.MatchConfigService;
import com.cloudmart.wish.service.MatchGroupService;
import com.cloudmart.wish.vo.MatchGroupCreatedVO;
import com.cloudmart.wish.vo.MatchGroupDetailVO;
import com.cloudmart.wish.vo.MatchGroupVO;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 同愿匹配 + 监督小队服务实现（Sprint 2.6，文档 2.8/十章）。
 *
 * <p>并发设计：加入小组走 CAS UPDATE（{@code member_count < max_members
 * AND status='OPEN'}）占位，未命中即 409 WISH_GROUP_FULL——并发 2 人
 * 抢 1 名额仅 1 人成功（文档验收）；同组同用户仅一条 ACTIVE 记录由
 * 功能唯一索引兜底（与 CAS 双保险，文档 1.2 ⑧）。</p>
 *
 * <p>同城口径：无城市名库，以创建人活跃公开心愿 geohash 前 4 字符
 * （约 39km 尺度）作为 city_code 同城代理（契约偏差已留档）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchGroupServiceImpl implements MatchGroupService {

    /** 被踢冷却 Redis Key 前缀：wish:lock:kicked:{userId}:{keyword}，TTL 24h（文档 2.8） */
    private static final String KICKED_COOLDOWN_KEY = "wish:lock:kicked:";
    private static final Duration KICKED_COOLDOWN_TTL = Duration.ofHours(24);

    /** 推荐候选窗口：评分排序在窗口内进行（P95 < 1s 的规模上限保护） */
    private static final int RECOMMEND_CANDIDATE_WINDOW = 500;

    private static final String RATE_KEY_PREFIX = "wish:rate:user:";

    private final MatchGroupMapper groupMapper;
    private final MatchMemberMapper memberMapper;
    private final WishUserStatMapper userStatMapper;
    private final WishMapper wishMapper;
    private final MatchConfigService matchConfigService;
    private final MatchEventProducer matchEventProducer;
    private final UserFeignClient userFeignClient;
    private final StringRedisTemplate redisTemplate;

    // ---------------- 建组 ----------------

    @Override
    @Transactional
    public MatchGroupCreatedVO createGroup(Long userId, CreateMatchGroupRequest request) {
        String keyword = request.keyword() == null ? "" : request.keyword().trim();
        if (keyword.isEmpty()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "关键词不能为空");
        }
        int maxMembers = request.maxMembers() == null ? 4 : request.maxMembers();
        if (maxMembers < 2 || maxMembers > 4) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "小组容量须为 2-4 人");
        }

        // 被踢冷却同样约束建组（同关键词防绕过：被踢后自建新组继续骚扰）
        checkKickedCooldown(userId, keyword);

        // 一人同关键词仅一个进行中的小组（防占坑；退出后可重新建组，无冷却）
        if (hasActiveGroupWithKeyword(userId, keyword)) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_KEYWORD_DUPLICATED,
                    "你已在同主题的小队中，先退出后再创建");
        }

        // 建组日限频（429 WISH_RATE_LIMITED，契约指定）
        int createLimit = matchConfigService.getIntConfig("match.create_daily_limit", 3);
        if (!checkDailyLimit(userId, "match_group_create", createLimit)) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "今天建的小队有点多，明天再来吧");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        MatchGroup group = new MatchGroup();
        group.setKeyword(keyword);
        group.setMaxMembers(maxMembers);
        group.setWishId(request.wishId());
        group.setLeaderId(userId);
        group.setMemberCount(1);
        group.setCityCode(resolveCityCode(userId));
        group.setStatus(MatchGroupStatus.OPEN);
        groupMapper.insert(group);

        MatchMember leader = new MatchMember();
        leader.setGroupId(group.getId());
        leader.setUserId(userId);
        leader.setRole(MatchMemberRole.LEADER);
        leader.setStatus(MatchMemberStatus.ACTIVE);
        leader.setJoinedAt(now);
        memberMapper.insert(leader);

        log.info("同愿小组创建成功, groupId={}, userId={}, keyword={}", group.getId(), userId, keyword);
        return new MatchGroupCreatedVO(group.getId(), keyword, maxMembers,
                MatchGroupStatus.OPEN.name(), MatchMemberRole.LEADER.name(), now);
    }

    // ---------------- 匹配推荐 ----------------

    @Override
    public MatchGroupVO.MatchPage recommendGroups(Long userId, MatchRecommendQuery query) {
        int pageSize = query.safePageSize();
        int offset = parseOffset(query.cursor());

        // 候选窗口：OPEN 组 id DESC（新组优先展示）
        List<MatchGroup> candidates = groupMapper.selectList(new LambdaQueryWrapper<MatchGroup>()
                .eq(MatchGroup::getStatus, MatchGroupStatus.OPEN)
                .orderByDesc(MatchGroup::getId)
                .last("LIMIT " + RECOMMEND_CANDIDATE_WINDOW));
        if (candidates.isEmpty()) {
            return new MatchGroupVO.MatchPage(List.of(), null, false);
        }

        // 排除本人已加入的组（文档验收：不重复推荐已加入同组的用户）
        Set<Long> joinedGroupIds = userId == null ? Set.of() : myActiveGroupIds(userId);
        List<MatchGroup> pool = candidates.stream()
                .filter(g -> !joinedGroupIds.contains(g.getId()))
                .toList();
        if (pool.isEmpty()) {
            return new MatchGroupVO.MatchPage(List.of(), null, false);
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
        double wKeyword = matchConfigService.getDoubleConfig("match.weight_keyword", 0.4);
        double wCity = matchConfigService.getDoubleConfig("match.weight_city", 0.3);
        double wActivity = matchConfigService.getDoubleConfig("match.weight_activity", 0.3);
        double threshold = matchConfigService.getDoubleConfig("match.score_threshold", 0.15);

        String queryKeyword = trimmed(query.keyword());
        // 城市：query.city 优先，否则用请求者自己的同城代理（登录态）
        String queryCity = trimmed(query.city());
        if (queryCity == null && userId != null) {
            queryCity = resolveCityCode(userId);
        }
        List<String> userTags = userId == null ? List.of() : myActiveWishTags(userId);

        // 批量取成员活跃度（避免 N+1）
        Map<Long, List<LocalDateTime>> groupMemberActivity = loadGroupMemberActivity(pool);

        List<MatchGroupVO> scored = new ArrayList<>();
        for (MatchGroup group : pool) {
            double kw = MatchScoreCalculator.keywordScore(queryKeyword, userTags, group.getKeyword());
            double city = group.getCityCode() != null && group.getCityCode().equals(queryCity) ? 1.0 : 0.0;
            double activity = MatchScoreCalculator.groupActivityScore(
                    groupMemberActivity.getOrDefault(group.getId(), List.of()), nowUtc);
            var breakdown = MatchScoreCalculator.score(kw, city, activity,
                    wKeyword, wCity, wActivity, group.getKeyword());
            // 精确关键词命中不受阈值限制（契约可靠性与冷启动兜底）
            if (breakdown.total() < threshold && kw < 1.0) {
                continue;
            }
            scored.add(new MatchGroupVO(group.getId(), group.getKeyword(), group.getMemberCount(),
                    group.getMaxMembers(), null, null,
                    Math.round(breakdown.total() * 100.0) / 100.0,
                    String.join(" · ", breakdown.reasons()),
                    group.getStatus().name(), group.getCityCode(), group.getCreatedAt()));
        }
        scored.sort(Comparator.comparingDouble(MatchGroupVO::matchScore).reversed()
                .thenComparing(MatchGroupVO::groupId, Comparator.reverseOrder()));

        // 组长昵称/头像批量补齐（Fail-Open 占位）
        fillLeaderBriefs(scored, pool);

        int from = Math.min(offset, scored.size());
        int to = Math.min(from + pageSize, scored.size());
        List<MatchGroupVO> page = scored.subList(from, to);
        boolean hasMore = to < scored.size();
        String nextCursor = hasMore ? String.valueOf(to) : null;
        return new MatchGroupVO.MatchPage(page, nextCursor, hasMore);
    }

    // ---------------- 加入 ----------------

    @Override
    @Transactional
    public void joinGroup(Long userId, Long groupId, JoinGroupRequest request) {
        MatchGroup group = requireGroup(groupId);
        if (group.getStatus() == MatchGroupStatus.CLOSED) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_NOT_FOUND, "小队已解散");
        }

        checkKickedCooldown(userId, group.getKeyword());
        // 先判本组成员（更精确的错误语义），再判同关键词占坑
        if (isActiveMember(groupId, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_ALREADY_MEMBER, "你已在该小队中");
        }
        if (hasActiveGroupWithKeyword(userId, group.getKeyword())) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_KEYWORD_DUPLICATED,
                    "你已在同主题的小队中");
        }

        // CAS 占位：并发抢最后 1 个名额仅 1 人成功（文档验收）
        int affected = groupMapper.update(null, new LambdaUpdateWrapper<MatchGroup>()
                .setSql("member_count = member_count + 1")
                .eq(MatchGroup::getId, groupId)
                .eq(MatchGroup::getStatus, MatchGroupStatus.OPEN)
                .lt(MatchGroup::getMemberCount, group.getMaxMembers()));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_FULL, "小队已满员");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        MatchMember member = new MatchMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(MatchMemberRole.MEMBER);
        member.setStatus(MatchMemberStatus.ACTIVE);
        member.setJoinMessage(sanitizeMessage(request == null ? null : request.message()));
        member.setJoinedAt(now);
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException ex) {
            // CAS 与功能唯一索引双保险（文档 1.2 ⑧）；重复插入回滚占位
            throw new BusinessException(WishErrorCodes.WISH_ALREADY_MEMBER, "你已在该小队中");
        }

        // 占位后恰好满员 → FULL
        MatchGroup fresh = groupMapper.selectById(groupId);
        if (fresh != null && fresh.getMemberCount() >= fresh.getMaxMembers()
                && fresh.getStatus() == MatchGroupStatus.OPEN) {
            groupMapper.update(null, new LambdaUpdateWrapper<MatchGroup>()
                    .set(MatchGroup::getStatus, MatchGroupStatus.FULL)
                    .eq(MatchGroup::getId, groupId)
                    .eq(MatchGroup::getStatus, MatchGroupStatus.OPEN));
        }
        log.info("加入同愿小组, groupId={}, userId={}, count={}", groupId, userId,
                fresh != null ? fresh.getMemberCount() : null);
    }

    // ---------------- 退出 / 踢出 ----------------

    @Override
    @Transactional
    public void leaveOrKickMember(Long userId, Long groupId, Long targetUserId) {
        MatchGroup group = requireGroup(groupId);
        boolean selfLeave = targetUserId == null || targetUserId.equals(userId);
        MatchMember actor = requireActiveMember(groupId, userId);

        if (selfLeave) {
            leaveGroup(group, actor);
        } else {
            kickMember(group, actor, targetUserId);
        }
    }

    private void leaveGroup(MatchGroup group, MatchMember actor) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        boolean isLeader = actor.getRole() == MatchMemberRole.LEADER;

        // 组长退出：转让给最早加入的 ACTIVE MEMBER；无成员则组关闭（文档 2.8）
        if (isLeader) {
            MatchMember successor = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                            .eq(MatchMember::getGroupId, group.getId())
                            .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE)
                            .eq(MatchMember::getRole, MatchMemberRole.MEMBER)
                            .orderByAsc(MatchMember::getJoinedAt)
                            .last("LIMIT 1"))
                    .stream().findFirst().orElse(null);
            if (successor == null) {
                markMemberLeft(actor, now);
                closeGroup(group, now);
                log.info("组长退出且无可转让成员，小组关闭, groupId={}, userId={}", group.getId(), actor.getUserId());
                return;
            }
            memberMapper.update(null, new LambdaUpdateWrapper<MatchMember>()
                    .set(MatchMember::getRole, MatchMemberRole.LEADER)
                    .eq(MatchMember::getId, successor.getId()));
            markMemberLeft(actor, now);
            groupMapper.update(null, new LambdaUpdateWrapper<MatchGroup>()
                    .set(MatchGroup::getLeaderId, successor.getUserId())
                    .set(MatchGroup::getMemberCount, group.getMemberCount() - 1)
                    .set(MatchGroup::getStatus, MatchGroupStatus.OPEN)
                    .eq(MatchGroup::getId, group.getId()));
            matchEventProducer.publishSquadEvent(successor.getUserId(), group.getId(),
                    "你已成为小队队长", "原队长离开了小组「" + group.getKeyword() + "」，由你接任队长");
            log.info("组长退出并转让, groupId={}, oldLeader={}, newLeader={}",
                    group.getId(), actor.getUserId(), successor.getUserId());
            return;
        }

        markMemberLeft(actor, now);
        decrementCountAndReopen(group);
        log.info("成员退出小组, groupId={}, userId={}", group.getId(), actor.getUserId());
    }

    private void kickMember(MatchGroup group, MatchMember actor, Long targetUserId) {
        if (actor.getRole() != MatchMemberRole.LEADER) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_LEADER_REQUIRED, "只有队长可以移除成员");
        }
        if (targetUserId.equals(group.getLeaderId())) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_LEADER_REQUIRED, "不能移除队长");
        }
        MatchMember target = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                        .eq(MatchMember::getGroupId, group.getId())
                        .eq(MatchMember::getUserId, targetUserId)
                        .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE)
                        .last("LIMIT 1"))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(WishErrorCodes.WISH_GROUP_NOT_FOUND, "该成员不在小队中"));

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        target.setStatus(MatchMemberStatus.KICKED);
        target.setLeftAt(now);
        memberMapper.updateById(target);
        decrementCountAndReopen(group);

        // 24h 冷却：不可加入同关键词任何小组（防恶意加入后被踢循环）
        setKickedCooldown(targetUserId, group.getKeyword());
        matchEventProducer.publishSquadEvent(targetUserId, group.getId(),
                "你已被移出小队",
                "你已被移出小组「" + group.getKeyword() + "」，24 小时内无法加入同主题小队");
        log.info("成员被移出小组, groupId={}, targetUserId={}, byLeader={}", group.getId(), targetUserId, actor.getUserId());
    }

    // ---------------- 解散 ----------------

    @Override
    @Transactional
    public void dissolveGroup(Long userId, Long groupId) {
        MatchGroup group = requireGroup(groupId);
        MatchMember actor = requireActiveMember(groupId, userId);
        if (actor.getRole() != MatchMemberRole.LEADER) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_LEADER_REQUIRED, "只有队长可以解散小队");
        }
        dissolveInternal(group, LocalDateTime.now(ZoneId.of("UTC")));
    }

    /** 解散共用链路（组长解散与管理端强制解散复用）：关闭 + 成员置 LEFT + 通知 */
    private void dissolveInternal(MatchGroup group, LocalDateTime now) {
        groupMapper.update(null, new LambdaUpdateWrapper<MatchGroup>()
                .set(MatchGroup::getStatus, MatchGroupStatus.CLOSED)
                .set(MatchGroup::getClosedAt, now)
                .set(MatchGroup::getMemberCount, 0)
                .eq(MatchGroup::getId, group.getId())
                .in(MatchGroup::getStatus, MatchGroupStatus.OPEN, MatchGroupStatus.FULL));
        List<MatchMember> actives = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .eq(MatchMember::getGroupId, group.getId())
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE));
        for (MatchMember member : actives) {
            member.setStatus(MatchMemberStatus.LEFT);
            member.setLeftAt(now);
            memberMapper.updateById(member);
            if (!member.getUserId().equals(group.getLeaderId())) {
                matchEventProducer.publishSquadEvent(member.getUserId(), group.getId(),
                        "小队已解散",
                        "小组「" + group.getKeyword() + "」已解散，你可以去匹配新的同路人");
            }
        }
        log.info("小组解散, groupId={}, memberCount={}", group.getId(), actives.size());
    }

    // ---------------- 互相提醒 ----------------

    @Override
    public void remindMembers(Long userId, Long groupId, Long targetUserId) {
        MatchGroup group = requireGroup(groupId);
        requireActiveMember(groupId, userId);

        int dailyLimit = matchConfigService.getIntConfig("match.remind_daily_limit", 3);
        if (!checkDailyLimit(userId, "squad_remind", dailyLimit)) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "今天的提醒次数用完了");
        }

        int idleDays = matchConfigService.getIntConfig("match.remind_idle_days", 3);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime idleThreshold = nowUtc.minusDays(idleDays);

        List<MatchMember> actives = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .eq(MatchMember::getGroupId, groupId)
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE));
        List<Long> targets = actives.stream()
                .filter(m -> !m.getUserId().equals(userId))
                .filter(m -> targetUserId == null || m.getUserId().equals(targetUserId))
                .map(MatchMember::getUserId)
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "小队里没有可提醒的成员");
        }

        // 目标过滤 idle（指定目标时不过滤——点名提醒必达）
        List<Long> filtered = targets;
        if (targetUserId == null) {
            Map<Long, LocalDateTime> lastActives = loadLastActives(targets);
            filtered = targets.stream()
                    .filter(uid -> {
                        LocalDateTime last = lastActives.get(uid);
                        return last == null || last.isBefore(idleThreshold);
                    })
                    .toList();
            if (filtered.isEmpty()) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                        "组员们最近都很活跃，暂时不需要提醒");
            }
        }

        String senderNickname = fetchNickname(userId);
        for (Long target : filtered) {
            matchEventProducer.publishSquadRemind(target, groupId, group.getKeyword(), senderNickname);
        }
        log.info("同路人提醒已发送, groupId={}, sender={}, targets={}", groupId, userId, filtered.size());
    }

    // ---------------- 我的小组 / 详情 ----------------

    @Override
    public List<MatchGroupDetailVO> listMyGroups(Long userId) {
        List<MatchMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .eq(MatchMember::getUserId, userId)
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = memberships.stream().map(MatchMember::getGroupId).toList();
        Map<Long, MatchGroup> groups = groupMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(MatchGroup::getId, g -> g));
        return groupIds.stream()
                .map(groups::get)
                .filter(g -> g != null && g.getStatus() != MatchGroupStatus.CLOSED)
                .map(g -> buildDetail(g, userId))
                .toList();
    }

    @Override
    public MatchGroupDetailVO getGroupDetail(Long userId, Long groupId) {
        MatchGroup group = requireGroup(groupId);
        return buildDetail(group, userId);
    }

    // ---------------- 私有工具 ----------------

    private MatchGroupDetailVO buildDetail(MatchGroup group, Long viewerId) {
        List<MatchMember> actives = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .eq(MatchMember::getGroupId, group.getId())
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE)
                .orderByAsc(MatchMember::getJoinedAt));
        Map<Long, LocalDateTime> lastActives = loadLastActives(actives.stream().map(MatchMember::getUserId).toList());
        Map<Long, String[]> nicknameAvatars = fetchNicknameAvatars(actives.stream().map(MatchMember::getUserId).toList());

        LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
        List<MatchGroupDetailVO.MemberItem> members = actives.stream()
                .map(m -> {
                    LocalDateTime last = lastActives.get(m.getUserId());
                    Long idleDays = null;
                    if (last != null) {
                        idleDays = Duration.between(last, nowUtc).toDays();
                    }
                    String[] brief = nicknameAvatars.getOrDefault(m.getUserId(), new String[]{"心愿旅人", ""});
                    return new MatchGroupDetailVO.MemberItem(m.getUserId(), brief[0], brief[1],
                            m.getRole().name(), m.getStatus().name(), m.getJoinedAt(), idleDays);
                })
                .toList();

        String viewerRole = viewerId == null ? null : actives.stream()
                .filter(m -> m.getUserId().equals(viewerId))
                .findFirst()
                .map(m -> m.getRole().name())
                .orElse(null);

        return new MatchGroupDetailVO(group.getId(), group.getKeyword(), group.getMemberCount(),
                group.getMaxMembers(), group.getStatus().name(), group.getCityCode(),
                group.getCreatedAt(), viewerRole, members);
    }

    private void markMemberLeft(MatchMember member, LocalDateTime now) {
        member.setStatus(MatchMemberStatus.LEFT);
        member.setLeftAt(now);
        memberMapper.updateById(member);
    }

    private void decrementCountAndReopen(MatchGroup group) {
        int next = Math.max(0, group.getMemberCount() - 1);
        LambdaUpdateWrapper<MatchGroup> update = new LambdaUpdateWrapper<MatchGroup>()
                .set(MatchGroup::getMemberCount, next);
        if (group.getStatus() == MatchGroupStatus.FULL) {
            update.set(MatchGroup::getStatus, MatchGroupStatus.OPEN);
        }
        update.eq(MatchGroup::getId, group.getId());
        groupMapper.update(null, update);
        group.setMemberCount(next);
    }

    private void closeGroup(MatchGroup group, LocalDateTime now) {
        groupMapper.update(null, new LambdaUpdateWrapper<MatchGroup>()
                .set(MatchGroup::getStatus, MatchGroupStatus.CLOSED)
                .set(MatchGroup::getClosedAt, now)
                .set(MatchGroup::getMemberCount, 0)
                .eq(MatchGroup::getId, group.getId()));
    }

    private MatchGroup requireGroup(Long groupId) {
        MatchGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_NOT_FOUND, "小队不存在或已解散");
        }
        return group;
    }

    private MatchMember requireActiveMember(Long groupId, Long userId) {
        return memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                        .eq(MatchMember::getGroupId, groupId)
                        .eq(MatchMember::getUserId, userId)
                        .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE)
                        .last("LIMIT 1"))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(WishErrorCodes.WISH_GROUP_NOT_FOUND, "你不在该小队中"));
    }

    private boolean isActiveMember(Long groupId, Long userId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<MatchMember>()
                .eq(MatchMember::getGroupId, groupId)
                .eq(MatchMember::getUserId, userId)
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE)) > 0;
    }

    private boolean hasActiveGroupWithKeyword(Long userId, String keyword) {
        Set<Long> myGroupIds = myActiveGroupIds(userId);
        if (myGroupIds.isEmpty()) {
            return false;
        }
        return groupMapper.selectCount(new LambdaQueryWrapper<MatchGroup>()
                .in(MatchGroup::getId, myGroupIds)
                .eq(MatchGroup::getKeyword, keyword)) > 0;
    }

    private Set<Long> myActiveGroupIds(Long userId) {
        return memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                        .eq(MatchMember::getUserId, userId)
                        .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE))
                .stream().map(MatchMember::getGroupId).collect(Collectors.toSet());
    }

    private List<String> myActiveWishTags(Long userId) {
        List<Wish> wishes = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getUserId, userId)
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getStatus, WishStatus.ACTIVE)
                .isNotNull(Wish::getTags)
                .last("LIMIT 20"));
        List<String> tags = new ArrayList<>();
        for (Wish wish : wishes) {
            List<String> wishTags = WishJsonUtils.parseStringList(wish.getTags());
            if (wishTags != null) {
                tags.addAll(wishTags);
            }
        }
        return tags;
    }

    /** 同城代理：最新活跃公开心愿的 geohash 前 4 字符（无则 null） */
    private String resolveCityCode(Long userId) {
        Wish wish = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getUserId, userId)
                        .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                        .in(Wish::getStatus, WishStatus.ACTIVE, WishStatus.FULFILLED)
                        .isNotNull(Wish::getGeohash)
                        .orderByDesc(Wish::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        String geohash = wish == null ? null : wish.getGeohash();
        if (geohash == null || geohash.length() < 4) {
            return null;
        }
        return geohash.substring(0, 4);
    }

    /** 批量加载各候选组的 ACTIVE 成员 last_active_at（P95 保护：两条 SQL） */
    private Map<Long, List<LocalDateTime>> loadGroupMemberActivity(List<MatchGroup> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        List<Long> groupIds = groups.stream().map(MatchGroup::getId).toList();
        List<MatchMember> members = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .in(MatchMember::getGroupId, groupIds)
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE));
        if (members.isEmpty()) {
            return Map.of();
        }
        Set<Long> memberUserIds = members.stream().map(MatchMember::getUserId).collect(Collectors.toSet());
        Map<Long, LocalDateTime> lastActives = userStatMapper.selectBatchIds(memberUserIds).stream()
                .collect(Collectors.toMap(WishUserStat::getUserId, WishUserStat::getLastActiveAt,
                        (a, b) -> a));

        Map<Long, List<LocalDateTime>> result = new HashMap<>();
        for (MatchMember member : members) {
            result.computeIfAbsent(member.getGroupId(), k -> new ArrayList<>())
                    .add(lastActives.get(member.getUserId()));
        }
        return result;
    }

    private Map<Long, LocalDateTime> loadLastActives(java.util.Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userStatMapper.selectBatchIds(new HashSet<>(userIds)).stream()
                    .filter(s -> s.getLastActiveAt() != null)
                    .collect(Collectors.toMap(WishUserStat::getUserId, WishUserStat::getLastActiveAt,
                            (a, b) -> a));
        } catch (Exception ex) {
            log.warn("批量加载用户活跃时间失败（Fail-Open）: {}", ex.getMessage());
            return Map.of();
        }
    }

    private void fillLeaderBriefs(List<MatchGroupVO> scored, List<MatchGroup> pool) {
        Map<Long, String[]> briefs = fetchNicknameAvatars(
                pool.stream().map(MatchGroup::getLeaderId).collect(Collectors.toSet()));
        Map<Long, MatchGroup> byId = pool.stream()
                .collect(Collectors.toMap(MatchGroup::getId, g -> g, (a, b) -> a));
        for (int i = 0; i < scored.size(); i++) {
            MatchGroup group = byId.get(scored.get(i).groupId());
            if (group == null) {
                continue;
            }
            String[] brief = briefs.getOrDefault(group.getLeaderId(), new String[]{"心愿旅人", ""});
            scored.set(i, new MatchGroupVO(scored.get(i).groupId(), scored.get(i).keyword(),
                    scored.get(i).memberCount(), scored.get(i).maxMembers(),
                    brief[0], brief[1], scored.get(i).matchScore(), scored.get(i).matchReason(),
                    scored.get(i).status(), scored.get(i).cityCode(), scored.get(i).createdAt()));
        }
    }

    /** 批量昵称/头像（仅暴露昵称头像，Fail-Open 占位；安全验收：不泄露手机号/邮箱） */
    private Map<Long, String[]> fetchNicknameAvatars(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(new HashSet<>(userIds)));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .filter(m -> m.get("id") instanceof Number)
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> new String[]{
                                        (String) m.getOrDefault("nickname", "心愿旅人"),
                                        (String) m.getOrDefault("avatar", "")},
                                (a, b) -> a));
            }
        } catch (Exception ex) {
            log.warn("批量获取成员昵称头像失败，降级占位: {}", ex.getMessage());
        }
        return Map.of();
    }

    private String fetchNickname(Long userId) {
        var briefs = fetchNicknameAvatars(Set.of(userId));
        String[] brief = briefs.get(userId);
        return brief == null ? null : brief[0];
    }

    private void checkKickedCooldown(Long userId, String keyword) {
        String key = KICKED_COOLDOWN_KEY + userId + ":" + keyword;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                throw new BusinessException(WishErrorCodes.WISH_KICKED_COOLDOWN,
                        "被移出同主题小队后 24 小时内无法加入，明天再来吧");
            }
        } catch (DataAccessException ex) {
            // Redis 异常 Fail-Open：冷却属防骚扰优化，DB 唯一约束兜底数据一致性
            log.warn("被踢冷却检查 Redis 异常，降级放行: {}", ex.getMessage());
        }
    }

    private void setKickedCooldown(Long userId, String keyword) {
        String key = KICKED_COOLDOWN_KEY + userId + ":" + keyword;
        try {
            redisTemplate.opsForValue().set(key, "1", KICKED_COOLDOWN_TTL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (DataAccessException ex) {
            log.warn("被踢冷却写入 Redis 异常（Fail-Open）: {}", ex.getMessage());
        }
    }

    /** 每日限频（与 AiRateLimiter 同模式：用户时区当日 23:59 过期，Fail-Open 放行） */
    private boolean checkDailyLimit(Long userId, String type, int limit) {
        String key = RATE_KEY_PREFIX + userId + ":" + type;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                ZoneId zone = resolveUserZone(userId);
                LocalDateTime endOfDay = java.time.LocalDate.now(zone).atTime(23, 59, 59);
                redisTemplate.expireAt(key, new java.util.Date(endOfDay.atZone(zone).toEpochSecond() * 1000));
            }
            return count <= limit;
        } catch (DataAccessException ex) {
            log.warn("限频 Redis 异常，降级放行（Fail-Open）, key={}", key, ex);
            return true;
        }
    }

    private ZoneId resolveUserZone(Long userId) {
        try {
            WishUserStat stat = userStatMapper.selectById(userId);
            if (stat != null && stat.getTimezone() != null && !stat.getTimezone().isBlank()) {
                return ZoneId.of(stat.getTimezone());
            }
        } catch (Exception ex) {
            log.warn("用户时区解析失败，回退系统默认: {}", ex.getMessage());
        }
        return ZoneId.systemDefault();
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        // 入组留言做基础净化：去控制字符 + <> 转义防存储型 XSS（与评论净化同思路的轻量版）
        trimmed = trimmed.replaceAll("[\\p{Cntrl}]", "");
        trimmed = trimmed.replace("<", "&lt;").replace(">", "&gt;");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int parseOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(cursor.trim());
            return Math.max(0, offset);
        } catch (NumberFormatException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的游标格式");
        }
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}

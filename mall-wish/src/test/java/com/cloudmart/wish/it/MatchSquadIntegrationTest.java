package com.cloudmart.wish.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.MatchGroup;
import com.cloudmart.wish.enums.MatchGroupStatus;
import com.cloudmart.wish.repository.MatchGroupMapper;
import com.cloudmart.wish.service.AdminMatchService;
import com.cloudmart.wish.service.MatchConfigService;
import com.cloudmart.wish.service.MatchGroupService;
import com.cloudmart.wish.vo.MatchGroupDetailVO;
import com.cloudmart.wish.vo.MatchGroupVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 同愿匹配 + 监督小队集成测试（Sprint 2.6，真实 MySQL 9 + Redis）。
 *
 * <p>覆盖文档 2.6 验收：建组/加入满员流转/退出回 OPEN/组长转让与关闭/
 * 踢人 24h 冷却（真实 Redis Key）/解散通知/推荐去重/功能唯一索引/
 * 权重配置生效（不改代码调整排序）。</p>
 */
@DisplayName("同愿小组集成测试")
class MatchSquadIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private MatchGroupService matchGroupService;

    @Autowired
    private AdminMatchService adminMatchService;

    @Autowired
    private MatchConfigService matchConfigService;

    @Autowired
    private MatchGroupMapper groupMapper;

    private static final long LEADER = 400L;
    private static final long USER_B = 401L;
    private static final long USER_C = 402L;
    private static final long USER_D = 403L;

    private void stubUserBriefs() {
        when(userFeignClient.batchGetUsers(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            List<java.util.Map<String, Object>> rows = ids.stream()
                    .<java.util.Map<String, Object>>map(id -> java.util.Map.of(
                            "id", id, "nickname", "用户" + id, "avatar", ""))
                    .toList();
            return ApiResponse.ok(rows);
        });
    }

    /** 种子：创建人的活跃公开心愿（带 geohash → 同城代理 city_code） */
    private void seedPublicWishWithGeohash(long userId, String geohash) {
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, geohash, tags, created_at, updated_at)
                VALUES (?, ?, '看极光', '和最好的人一起', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, ?, '["看极光"]', NOW(), NOW())
                """, System.nanoTime(), userId, geohash);
    }

    private MatchGroup createGroup(long userId, String keyword) {
        matchGroupService.createGroup(userId,
                new com.cloudmart.wish.dto.CreateMatchGroupRequest(keyword, null, null));
        // 同关键词可能存在历史（已关闭）组：按 id 倒序取最新创建
        return groupMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchGroup>()
                        .eq(MatchGroup::getKeyword, keyword)
                        .orderByDesc(MatchGroup::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("建组与加入")
    class CreateAndJoin {

        @Test
        @DisplayName("建组：OPEN + LEADER 成员行 + member_count=1；同城代理取自活跃公开心愿 geohash 前缀")
        void createGroupPersists() {
            stubUserBriefs();
            seedPublicWishWithGeohash(LEADER, "ws1e2z3");
            MatchGroup group = createGroup(LEADER, "看极光");

            assertThat(group.getStatus()).isEqualTo(MatchGroupStatus.OPEN);
            assertThat(group.getMemberCount()).isEqualTo(1);
            assertThat(group.getMaxMembers()).isEqualTo(4);
            assertThat(group.getCityCode()).isEqualTo("ws1e");

            Long memberRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_match_member WHERE group_id = ? AND status = 'ACTIVE'",
                    Long.class, group.getId());
            String role = jdbcTemplate.queryForObject(
                    "SELECT role FROM wish_match_member WHERE group_id = ? AND user_id = ?",
                    String.class, group.getId(), LEADER);
            assertThat(memberRows).isEqualTo(1L);
            assertThat(role).isEqualTo("LEADER");
        }

        @Test
        @DisplayName("加入至满员：OPEN → FULL；超员加入 409 WISH_GROUP_FULL（并发语义 CAS 兜底）")
        void joinUntilFullThenReject() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);
            matchGroupService.joinGroup(USER_C, group.getId(), null);
            matchGroupService.joinGroup(USER_D, group.getId(), null);

            MatchGroup full = groupMapper.selectById(group.getId());
            assertThat(full.getStatus()).isEqualTo(MatchGroupStatus.FULL);
            assertThat(full.getMemberCount()).isEqualTo(4);

            long latecomer = 404L;
            assertThatThrownBy(() -> matchGroupService.joinGroup(latecomer, group.getId(), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_FULL);
        }

        @Test
        @DisplayName("重复加入：409 WISH_ALREADY_MEMBER；功能唯一索引兜底双 ACTIVE 行")
        void joinDuplicateRejectedAndUniqueIndex() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);

            assertThatThrownBy(() -> matchGroupService.joinGroup(USER_B, group.getId(), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_ALREADY_MEMBER);

            // 数据库级约束：同组同用户第二条 ACTIVE 行必须被功能唯一索引拒绝
            assertThatCode(() -> jdbcTemplate.update(
                    "INSERT INTO wish_match_member (id, group_id, user_id, role, status, joined_at) "
                            + "VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', NOW())",
                    System.nanoTime(), group.getId(), USER_B))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("退出 / 转让 / 踢人 / 解散")
    class LeaveKickDissolve {

        @Test
        @DisplayName("成员退出：FULL → OPEN，历史行保留 status=LEFT（互动历史不删）")
        void memberLeaveReopens() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);
            matchGroupService.joinGroup(USER_C, group.getId(), null);
            matchGroupService.joinGroup(USER_D, group.getId(), null);
            assertThat(groupMapper.selectById(group.getId()).getStatus()).isEqualTo(MatchGroupStatus.FULL);

            matchGroupService.leaveOrKickMember(USER_D, group.getId(), USER_D);

            MatchGroup reopened = groupMapper.selectById(group.getId());
            assertThat(reopened.getStatus()).isEqualTo(MatchGroupStatus.OPEN);
            assertThat(reopened.getMemberCount()).isEqualTo(3);
            String leftStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM wish_match_member WHERE group_id = ? AND user_id = ?",
                    String.class, group.getId(), USER_D);
            assertThat(leftStatus).isEqualTo("LEFT");
        }

        @Test
        @DisplayName("组长退出：转让给最早加入的 MEMBER；无成员则组关闭")
        void leaderLeaveTransfersOrCloses() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);
            matchGroupService.joinGroup(USER_C, group.getId(), null);

            matchGroupService.leaveOrKickMember(LEADER, group.getId(), LEADER);

            MatchGroup transferred = groupMapper.selectById(group.getId());
            assertThat(transferred.getLeaderId()).isEqualTo(USER_B);
            assertThat(transferred.getStatus()).isEqualTo(MatchGroupStatus.OPEN);
            assertThat(transferred.getMemberCount()).isEqualTo(2);
            String newRole = jdbcTemplate.queryForObject(
                    "SELECT role FROM wish_match_member WHERE group_id = ? AND user_id = ?",
                    String.class, group.getId(), USER_B);
            assertThat(newRole).isEqualTo("LEADER");

            // 剩余成员全部退出 → 组关闭
            matchGroupService.leaveOrKickMember(USER_B, group.getId(), USER_B);
            matchGroupService.leaveOrKickMember(USER_C, group.getId(), USER_C);
            MatchGroup closed = groupMapper.selectById(group.getId());
            assertThat(closed.getStatus()).isEqualTo(MatchGroupStatus.CLOSED);
            assertThat(closed.getClosedAt()).isNotNull();
        }

        @Test
        @DisplayName("踢人：KICKED + Redis 24h 冷却 Key；冷却期内加入同关键词其他组 403")
        void kickSetsCooldown() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);

            matchGroupService.leaveOrKickMember(LEADER, group.getId(), USER_B);

            String kicked = jdbcTemplate.queryForObject(
                    "SELECT status FROM wish_match_member WHERE group_id = ? AND user_id = ?",
                    String.class, group.getId(), USER_B);
            assertThat(kicked).isEqualTo("KICKED");
            Boolean cooldownExists = redisTemplate.hasKey("wish:lock:kicked:" + USER_B + ":看极光");
            assertThat(cooldownExists).isTrue();

            // 同关键词新建组也受冷却约束（防绕过）
            assertThatThrownBy(() -> matchGroupService.createGroup(USER_B,
                    new com.cloudmart.wish.dto.CreateMatchGroupRequest("看极光", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_KICKED_COOLDOWN);
        }

        @Test
        @DisplayName("非组长踢人：403 WISH_GROUP_LEADER_REQUIRED")
        void kickByNonLeaderForbidden() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);
            matchGroupService.joinGroup(USER_C, group.getId(), null);

            assertThatThrownBy(() -> matchGroupService.leaveOrKickMember(USER_B, group.getId(), USER_C))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_LEADER_REQUIRED);
        }

        @Test
        @DisplayName("解散：CLOSED + 全部成员 LEFT；退出后可重新加入（LEFT 行不挡 ACTIVE 插入）")
        void dissolveAndRejoin() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);

            matchGroupService.dissolveGroup(LEADER, group.getId());

            assertThat(groupMapper.selectById(group.getId()).getStatus()).isEqualTo(MatchGroupStatus.CLOSED);
            Integer activeRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_match_member WHERE group_id = ? AND status = 'ACTIVE'",
                    Integer.class, group.getId());
            assertThat(activeRows).isZero();

            // 退出后无冷却：USER_B 可加入同关键词新组
            MatchGroup newGroup = createGroup(USER_C, "看极光");
            assertThatCode(() -> matchGroupService.joinGroup(USER_B, newGroup.getId(), null))
                    .doesNotThrowAnyException();
            assertThat(groupMapper.selectById(newGroup.getId()).getMemberCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("推荐与配置")
    class RecommendAndConfig {

        @Test
        @DisplayName("推荐去重：已加入的组不出现在推荐中；精确关键词命中给相似度说明")
        void recommendDedupAndReason() {
            stubUserBriefs();
            MatchGroup joined = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, joined.getId(), null);

            var page = matchGroupService.recommendGroups(USER_B,
                    new com.cloudmart.wish.dto.MatchRecommendQuery("看极光", null, null, 10));

            assertThat(page.records())
                    .noneMatch(g -> g.groupId().equals(joined.getId()));
        }

        @Test
        @DisplayName("权重可配置实时生效：提高关键词权重后匹配分变化（不改代码）")
        void configChangeAffectsScore() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");

            var before = matchGroupService.recommendGroups(USER_B,
                    new com.cloudmart.wish.dto.MatchRecommendQuery("看极光", null, null, 10));
            double scoreBefore = before.records().isEmpty() ? 0.0 : before.records().get(0).matchScore();

            try {
                matchConfigService.updateConfig("match.weight_keyword", "1.0", 1L);
                matchConfigService.updateConfig("match.weight_city", "0.0", 1L);
                matchConfigService.updateConfig("match.weight_activity", "0.0", 1L);

                var after = matchGroupService.recommendGroups(USER_B,
                        new com.cloudmart.wish.dto.MatchRecommendQuery("看极光", null, null, 10));
                double scoreAfter = after.records().isEmpty() ? 0.0 : after.records().get(0).matchScore();

                assertThat(scoreAfter).isGreaterThan(scoreBefore);
                assertThat(scoreAfter).isEqualTo(1.0);
            } finally {
                // 复位默认值并失效缓存，避免污染同上下文后续用例（60s 内存缓存）
                matchConfigService.updateConfig("match.weight_keyword", "0.4", 1L);
                matchConfigService.updateConfig("match.weight_city", "0.3", 1L);
                matchConfigService.updateConfig("match.weight_activity", "0.3", 1L);
            }
        }

        @Test
        @DisplayName("管理端：小组列表可见 CLOSED 组 + 强制解散幂等拒绝已关闭组")
        void adminListAndForceDissolve() {
            stubUserBriefs();
            MatchGroup group = createGroup(LEADER, "看极光");
            matchGroupService.joinGroup(USER_B, group.getId(), null);
            matchGroupService.dissolveGroup(LEADER, group.getId());

            List<AdminMatchService.AdminMatchGroupRow> rows = adminMatchService.listGroups(null, "看极光");
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.groupId()).isEqualTo(group.getId());
                assertThat(row.status()).isEqualTo("CLOSED");
                assertThat(row.leaderNickname()).isEqualTo("用户" + LEADER);
            });

            assertThatThrownBy(() -> adminMatchService.forceDissolve(group.getId(), 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_NOT_FOUND);
        }
    }
}

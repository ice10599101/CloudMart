package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.CommunityActivity;
import com.cloudmart.wish.service.ActivityService;
import com.cloudmart.wish.service.impl.ActivityConditionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 社区活动集成测试（Sprint 3.5，真实 MySQL+Redis）。
 *
 * <p>覆盖文档 3.5 验收：状态机流转/列表仅 ACTIVE（归档入口消失详情可访问）/
 * 奖励幂等/合伙人全流程（申请→审批→看板）/条件解析判定/进度 Redis 计数。</p>
 */
@DisplayName("社区活动集成测试")
class ActivityIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private ActivityService activityService;

    private static final long ADMIN = 1L;
    private static final long LEADER = 880L;
    private static final long MEMBER_A = 881L;
    private static final long MEMBER_B = 882L;

    private CommunityActivity seedPartnerActivity() {
        CommunityActivity activity = new CommunityActivity();
        activity.setType(com.cloudmart.wish.enums.ActivityType.WISH_PARTNER);
        activity.setTitle("极光摄影合伙人");
        activity.setConditionJson(
                "{\"type\":\"MEMBER_FULFILLED\",\"skills\":[\"design\",\"video\"]}");
        activity.setRewardJson("{\"starlight\":100,\"badgeCode\":null}");
        activity.setCreatedBy(LEADER);
        return activityService.create(activity, ADMIN);
    }

    private long seedWish(long userId, String title, String status) {
        long wishId = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, created_at, updated_at)
                VALUES (?, ?, ?, '测试', 1, 'PUBLIC', ?, 'APPROVED', 1, NOW(), NOW())
                """, wishId, userId, title, status);
        return wishId;
    }

    @Nested
    @DisplayName("状态机与归档")
    class StateMachine {

        @Test
        @DisplayName("状态机：DRAFT → ACTIVE → ENDED → ARCHIVED；非法流转 409")
        void transitions() {
            CommunityActivity activity = seedPartnerActivity();
            Long id = activity.getId();

            assertThatThrownBy(() -> activityService.transition(id, "end", ADMIN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT);

            assertThat(activityService.transition(id, "start", ADMIN).getStatus())
                    .isEqualTo(com.cloudmart.wish.enums.ActivityStatus.ACTIVE);
            assertThat(activityService.transition(id, "end", ADMIN).getStatus())
                    .isEqualTo(com.cloudmart.wish.enums.ActivityStatus.ENDED);
            assertThat(activityService.transition(id, "archive", ADMIN).getStatus())
                    .isEqualTo(com.cloudmart.wish.enums.ActivityStatus.ARCHIVED);
        }

        @Test
        @DisplayName("归档：列表入口消失，详情页仍可访问（验收）")
        void archivedDetailAccessible() {
            CommunityActivity activity = seedPartnerActivity();
            activityService.transition(activity.getId(), "start", ADMIN);

            assertThat(activityService.listActivities(null, null))
                    .extracting(CommunityActivity::getId)
                    .contains(activity.getId());

            activityService.transition(activity.getId(), "end", ADMIN);
            activityService.transition(activity.getId(), "archive", ADMIN);

            // 列表消失
            assertThat(activityService.listActivities(null, null))
                    .extracting(CommunityActivity::getId)
                    .doesNotContain(activity.getId());
            // 详情仍可访问
            assertThat(activityService.getActivity(activity.getId()).getTitle())
                    .isEqualTo("极光摄影合伙人");
        }

        @Test
        @DisplayName("删除：仅 DRAFT 可删；ACTIVE 删除拒绝")
        void deleteOnlyDraft() {
            CommunityActivity draft = seedPartnerActivity();
            activityService.delete(draft.getId());
            assertThatThrownBy(() -> activityService.getActivity(draft.getId()))
                    .isInstanceOf(BusinessException.class);

            CommunityActivity active = seedPartnerActivity();
            activityService.transition(active.getId(), "start", ADMIN);
            assertThatThrownBy(() -> activityService.delete(active.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("合伙人协作")
    class PartnerFlow {

        @Test
        @DisplayName("全流程：申请（匹配度计算）→ 审批 → 看板（协作进度共享）→ 成员达成 → 奖励幂等")
        void partnerFullFlow() {
            CommunityActivity activity = seedPartnerActivity();
            Long activityId = activity.getId();
            activityService.transition(activityId, "start", ADMIN);
            long wishA = seedWish(LEADER, "极光之旅", "ACTIVE");

            // 招募发起人（活动创建者）占位：JOINED
            activityService.join(ADMIN, activityId);
            // 发起人绑定期望协作的心愿
            jdbcTemplate.update(
                    "UPDATE wish_activity_participant SET wish_id = ?, role = 'LEADER' "
                            + "WHERE activity_id = ? AND user_id = ?",
                    wishA, activityId, ADMIN);

            // MEMBER_A 申请（技能部分匹配）
            long wishMemberA = seedWish(MEMBER_A, "协助摄影", "ACTIVE");
            activityService.applyPartner(MEMBER_A, activityId, wishMemberA, List.of("design"));

            // MEMBER_B 申请（技能全匹配）
            long wishMemberB = seedWish(MEMBER_B, "协助剪辑", "ACTIVE");
            activityService.applyPartner(MEMBER_B, activityId, wishMemberB, List.of("design", "video"));

            // 匹配度：required=[design,video]，A 交 design → 50；B 全中 → 100
            Integer scoreA = jdbcTemplate.queryForObject(
                    "SELECT match_score FROM wish_activity_participant "
                            + "WHERE activity_id = ? AND user_id = ?",
                    Integer.class, activityId, MEMBER_A);
            Integer scoreB = jdbcTemplate.queryForObject(
                    "SELECT match_score FROM wish_activity_participant "
                            + "WHERE activity_id = ? AND user_id = ?",
                    Integer.class, activityId, MEMBER_B);
            assertThat(scoreA).isEqualTo(50);
            assertThat(scoreB).isEqualTo(100);

            // 审批（招募发起人 = 活动创建者 ADMIN）
            activityService.reviewApplication(ADMIN, activityId, MEMBER_A, true);
            activityService.reviewApplication(ADMIN, activityId, MEMBER_B, true);

            // 条件未达成（无成员还愿）→ 发奖拒绝
            assertThatThrownBy(() -> activityService.issueRewards(activityId, ADMIN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);

            // 成员达成（B 心愿还愿）
            jdbcTemplate.update("UPDATE wish SET status = 'FULFILLED' WHERE id = ?", wishMemberB);

            // 看板：成员进度/打卡/最新成长共享（组内可见；创建者=LEADER 视角）
            var members = activityService.getPartnerBoard(activityId, ADMIN);
            assertThat(members).hasSize(3);

            // 奖励发放：seedUserStat 让星光入账可计算
            seedUserStat(ADMIN, 0);
            seedUserStat(MEMBER_A, 0);
            seedUserStat(MEMBER_B, 0);
            var stats = activityService.issueRewards(activityId, ADMIN);
            assertThat(stats.eligible()).isEqualTo(3);
            assertThat(stats.starlightIssued()).isEqualTo(3);

            // 幂等：重复发放全部跳过
            var stats2 = activityService.issueRewards(activityId, ADMIN);
            assertThat(stats2.starlightIssued()).isZero();
            assertThat(stats2.skipped()).isEqualTo(3);
        }

        @Test
        @DisplayName("非作者审批 → 403")
        void reviewByNonLeaderForbidden() {
            CommunityActivity activity = seedPartnerActivity();
            Long activityId = activity.getId();
            activityService.transition(activityId, "start", ADMIN);
            long wishMember = seedWish(MEMBER_A, "协助", "ACTIVE");
            activityService.applyPartner(MEMBER_A, activityId, wishMember, List.of("design"));

            assertThatThrownBy(() -> activityService.reviewApplication(
                    MEMBER_B, activityId, MEMBER_A, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("条件解析")
    class ConditionParser {

        @Test
        @DisplayName("条件判定：进度计数阈值/参与人数阈值/成员达成（验收项）")
        void conditionVerdicts() {
            String progressCondition = "{\"type\":\"PROGRESS_COUNTER\",\"threshold\":100}";
            assertThat(ActivityConditionParser.isMet(progressCondition, 100, 0, false)).isTrue();
            assertThat(ActivityConditionParser.isMet(progressCondition, 99, 0, false)).isFalse();

            String participantCondition = "{\"type\":\"PARTICIPANT_COUNT\",\"threshold\":50}";
            assertThat(ActivityConditionParser.isMet(participantCondition, 0, 50, false)).isTrue();
            assertThat(ActivityConditionParser.isMet(participantCondition, 0, 49, false)).isFalse();

            String memberCondition = "{\"type\":\"MEMBER_FULFILLED\"}";
            assertThat(ActivityConditionParser.isMet(memberCondition, 0, 0, true)).isTrue();
            assertThat(ActivityConditionParser.isMet(memberCondition, 0, 0, false)).isFalse();
        }

        @Test
        @DisplayName("非法条件：未知类型/非法 JSON → IllegalArgumentException（编辑器校验）")
        void invalidConditionRejected() {
            assertThatThrownBy(() -> ActivityConditionParser.validate("{\"type\":\"UNKNOWN\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ActivityConditionParser.validate("not-json"))
                    .isInstanceOf(IllegalArgumentException.class);
            // null 条件 = 无条件配置，不校验（参与即达标语义）
            org.assertj.core.api.Assertions.assertThatCode(
                    () -> ActivityConditionParser.validate(null)).doesNotThrowAnyException();
        }
    }
}

package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateMatchGroupRequest;
import com.cloudmart.wish.dto.JoinGroupRequest;
import com.cloudmart.wish.dto.MatchRecommendQuery;
import com.cloudmart.wish.entity.MatchGroup;
import com.cloudmart.wish.entity.MatchMember;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.MatchGroupStatus;
import com.cloudmart.wish.enums.MatchMemberRole;
import com.cloudmart.wish.enums.MatchMemberStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.mq.MatchEventProducer;
import com.cloudmart.wish.repository.MatchGroupMapper;
import com.cloudmart.wish.repository.MatchMemberMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.wish.service.MatchConfigService;
import com.cloudmart.wish.vo.MatchGroupVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同愿小组状态机与边界测试（Sprint 2.6 验收：OPEN/FULL/CLOSED 流转、
 * 并发名额 CAS、转让/解散、踢人冷却、推荐去重与冷启动）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("同愿小组服务")
class MatchGroupServiceImplTest {

    @Mock
    private MatchGroupMapper groupMapper;
    @Mock
    private MatchMemberMapper memberMapper;
    @Mock
    private WishUserStatMapper userStatMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private MatchConfigService matchConfigService;
    @Mock
    private MatchEventProducer matchEventProducer;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MatchGroupServiceImpl matchGroupService;

    private static final long USER = 100L;
    private static final long GROUP_ID = 900L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MatchGroup.class);
        TableInfoHelper.initTableInfo(assistant, MatchMember.class);
        TableInfoHelper.initTableInfo(assistant, Wish.class);
        TableInfoHelper.initTableInfo(assistant, com.cloudmart.wish.entity.WishUserStat.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(matchConfigService.getIntConfig(anyString(), anyInt())).thenReturn(3);
        lenient().when(matchConfigService.getDoubleConfig(anyString(), anyDouble()))
                .thenAnswer(inv -> (double) inv.getArgument(1));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(userFeignClient.batchGetUsers(any())).thenReturn(
                com.cloudmart.common.api.ApiResponse.ok(List.of()));
        lenient().when(wishMapper.selectList(any())).thenReturn(List.of());
        lenient().when(userStatMapper.selectBatchIds(any())).thenReturn(List.of());
    }

    private MatchGroup group(MatchGroupStatus status, int count, int max) {
        MatchGroup group = new MatchGroup();
        group.setId(GROUP_ID);
        group.setKeyword("看极光");
        group.setMaxMembers(max);
        group.setLeaderId(USER);
        group.setMemberCount(count);
        group.setStatus(status);
        group.setCreatedAt(LocalDateTime.now());
        return group;
    }

    private MatchMember member(long userId, MatchMemberRole role, MatchMemberStatus status) {
        MatchMember member = new MatchMember();
        member.setId(System.nanoTime());
        member.setGroupId(GROUP_ID);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(status);
        member.setJoinedAt(LocalDateTime.now());
        return member;
    }

    // ---------------- 建组 ----------------

    @Nested
    @DisplayName("建组")
    class CreateGroup {

        @Test
        @DisplayName("成功：OPEN + 创建者为 LEADER + memberCount=1")
        void createSuccess() {
            when(groupMapper.insert(any(MatchGroup.class))).thenAnswer(inv -> {
                ((MatchGroup) inv.getArgument(0)).setId(GROUP_ID);
                return 1;
            });

            var vo = matchGroupService.createGroup(USER,
                    new CreateMatchGroupRequest("看极光", null, null));

            assertThat(vo.status()).isEqualTo("OPEN");
            assertThat(vo.role()).isEqualTo("LEADER");
            assertThat(vo.maxMembers()).isEqualTo(4);
            ArgumentCaptor<MatchGroup> captor = ArgumentCaptor.forClass(MatchGroup.class);
            verify(groupMapper).insert(captor.capture());
            assertThat(captor.getValue().getMemberCount()).isEqualTo(1);
            verify(memberMapper).insert(any(MatchMember.class));
        }

        @Test
        @DisplayName("关键词空白/容量越界：400 WISH_VALIDATION_ERROR")
        void createValidation() {
            assertThatThrownBy(() -> matchGroupService.createGroup(USER,
                    new CreateMatchGroupRequest("  ", 4, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);

            assertThatThrownBy(() -> matchGroupService.createGroup(USER,
                    new CreateMatchGroupRequest("看极光", 5, null)))
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("同关键词已有进行中小队：409 WISH_GROUP_KEYWORD_DUPLICATED")
        void createKeywordDuplicated() {
            when(memberMapper.selectList(any())).thenReturn(
                    List.of(member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)));
            when(groupMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> matchGroupService.createGroup(USER,
                    new CreateMatchGroupRequest("看极光", 4, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_KEYWORD_DUPLICATED);
        }

        @Test
        @DisplayName("被踢冷却期（Redis key 存在）：403 WISH_KICKED_COOLDOWN")
        void createKickedCooldown() {
            when(redisTemplate.hasKey("wish:lock:kicked:" + USER + ":看极光")).thenReturn(true);

            assertThatThrownBy(() -> matchGroupService.createGroup(USER,
                    new CreateMatchGroupRequest("看极光", 4, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_KICKED_COOLDOWN);
        }

        @Test
        @DisplayName("建组日限频：429 WISH_RATE_LIMITED")
        void createRateLimited() {
            when(matchConfigService.getIntConfig(eq("match.create_daily_limit"), anyInt())).thenReturn(3);
            when(valueOperations.increment(anyString())).thenReturn(4L);

            assertThatThrownBy(() -> matchGroupService.createGroup(USER,
                    new CreateMatchGroupRequest("看极光", 4, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
        }
    }

    // ---------------- 加入 ----------------

    @Nested
    @DisplayName("加入小队")
    class JoinGroup {

        @Test
        @DisplayName("成功：CAS 占位命中；未满员不切 FULL")
        void joinSuccess() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.OPEN, 2, 4));
            when(groupMapper.update(any(), any())).thenReturn(1);
            when(memberMapper.selectList(any())).thenReturn(List.of());
            when(groupMapper.selectCount(any())).thenReturn(0L);

            matchGroupService.joinGroup(USER, GROUP_ID, new JoinGroupRequest("一起加油"));

            verify(memberMapper).insert(any(MatchMember.class));
            // CAS 占位恰好一次；未满员无额外状态切换
            verify(groupMapper).update(any(), any());
        }

        @Test
        @DisplayName("并发抢最后名额：CAS 未命中返回 409 WISH_GROUP_FULL（验收：仅 1 人成功）")
        void joinFullCasMiss() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.OPEN, 4, 4));
            when(groupMapper.update(any(), any())).thenReturn(0);

            BusinessException ex = catchThrowableOfType(() ->
                            matchGroupService.joinGroup(USER, GROUP_ID, null),
                    BusinessException.class);
            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_GROUP_FULL);
            verify(memberMapper, never()).insert(any(MatchMember.class));
        }

        @Test
        @DisplayName("FULL 状态组：409 WISH_GROUP_FULL")
        void joinFullStatus() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.FULL, 4, 4));

            assertThatThrownBy(() -> matchGroupService.joinGroup(USER, GROUP_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_FULL);
        }

        @Test
        @DisplayName("CLOSED 组：404 WISH_GROUP_NOT_FOUND")
        void joinClosed() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.CLOSED, 0, 4));

            assertThatThrownBy(() -> matchGroupService.joinGroup(USER, GROUP_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("已是成员：409 WISH_ALREADY_MEMBER")
        void joinAlreadyMember() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.OPEN, 2, 4));
            when(memberMapper.selectList(any())).thenReturn(
                    List.of(member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)));
            when(memberMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> matchGroupService.joinGroup(USER, GROUP_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_ALREADY_MEMBER);
        }

        @Test
        @DisplayName("被踢冷却（同关键词）：403 WISH_KICKED_COOLDOWN")
        void joinKickedCooldown() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.OPEN, 2, 4));
            when(redisTemplate.hasKey("wish:lock:kicked:" + USER + ":看极光")).thenReturn(true);

            assertThatThrownBy(() -> matchGroupService.joinGroup(USER, GROUP_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_KICKED_COOLDOWN);
        }
    }

    // ---------------- 退出 / 踢出 / 解散 ----------------

    @Nested
    @DisplayName("退出 / 踢出 / 解散")
    class LeaveKickDissolve {

        @Test
        @DisplayName("成员退出：置 LEFT + 计数递减 + FULL→OPEN")
        void memberLeave() {
            MatchGroup full = group(MatchGroupStatus.FULL, 4, 4);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(full);
            when(memberMapper.selectList(any())).thenReturn(
                    List.of(member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)));

            matchGroupService.leaveOrKickMember(USER, GROUP_ID, USER);

            ArgumentCaptor<MatchMember> captor = ArgumentCaptor.forClass(MatchMember.class);
            verify(memberMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MatchMemberStatus.LEFT);
            assertThat(captor.getValue().getLeftAt()).isNotNull();
            verify(groupMapper).update(any(), any());
        }

        @Test
        @DisplayName("组长退出：转让给最早加入的 MEMBER + 通知接任者")
        void leaderLeaveTransfers() {
            MatchGroup open = group(MatchGroupStatus.OPEN, 2, 4);
            open.setLeaderId(USER);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(open);
            MatchMember leader = member(USER, MatchMemberRole.LEADER, MatchMemberStatus.ACTIVE);
            MatchMember successor = member(200L, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            successor.setJoinedAt(LocalDateTime.now().minusDays(1));
            when(memberMapper.selectList(any()))
                    .thenReturn(List.of(leader))            // requireActiveMember
                    .thenReturn(List.of(successor));        // successor 查询

            matchGroupService.leaveOrKickMember(USER, GROUP_ID, USER);

            // 原队长置 LEFT；接任者角色经 LambdaUpdateWrapper 晋升为 LEADER
            ArgumentCaptor<MatchMember> captor = ArgumentCaptor.forClass(MatchMember.class);
            verify(memberMapper).updateById(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(USER);
            assertThat(captor.getValue().getStatus()).isEqualTo(MatchMemberStatus.LEFT);
            verify(memberMapper).update(any(), any());
            verify(groupMapper).update(any(), any());
            verify(matchEventProducer).publishSquadEvent(eq(200L), eq(GROUP_ID), anyString(), anyString());
        }

        @Test
        @DisplayName("组长退出且无成员：组关闭（CLOSED）")
        void leaderLeaveCloses() {
            MatchGroup open = group(MatchGroupStatus.OPEN, 1, 4);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(open);
            MatchMember leader = member(USER, MatchMemberRole.LEADER, MatchMemberStatus.ACTIVE);
            when(memberMapper.selectList(any()))
                    .thenReturn(List.of(leader))
                    .thenReturn(List.of());

            matchGroupService.leaveOrKickMember(USER, GROUP_ID, USER);

            verify(groupMapper).update(any(), any());
            verify(matchEventProducer, never()).publishSquadEvent(anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("非组长踢人：403 WISH_GROUP_LEADER_REQUIRED")
        void kickByNonLeader() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.OPEN, 2, 4));
            when(memberMapper.selectList(any())).thenReturn(
                    List.of(member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)));

            assertThatThrownBy(() -> matchGroupService.leaveOrKickMember(USER, GROUP_ID, 300L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_LEADER_REQUIRED);
        }

        @Test
        @DisplayName("组长踢人成功：KICKED + 24h 冷却 Key + 通知被踢者")
        void kickSuccess() {
            MatchGroup open = group(MatchGroupStatus.OPEN, 2, 4);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(open);
            MatchMember leader = member(USER, MatchMemberRole.LEADER, MatchMemberStatus.ACTIVE);
            MatchMember target = member(300L, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            when(memberMapper.selectList(any()))
                    .thenReturn(List.of(leader))
                    .thenReturn(List.of(target));

            matchGroupService.leaveOrKickMember(USER, GROUP_ID, 300L);

            ArgumentCaptor<MatchMember> captor = ArgumentCaptor.forClass(MatchMember.class);
            verify(memberMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MatchMemberStatus.KICKED);
            verify(valueOperations).set(eq("wish:lock:kicked:300:看极光"), eq("1"), anyLong(), any());
            verify(matchEventProducer).publishSquadEvent(eq(300L), eq(GROUP_ID), anyString(), anyString());
        }

        @Test
        @DisplayName("组长解散：全成员 LEFT + 非组长成员收到通知")
        void dissolveByLeader() {
            MatchGroup open = group(MatchGroupStatus.OPEN, 3, 4);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(open);
            MatchMember leader = member(USER, MatchMemberRole.LEADER, MatchMemberStatus.ACTIVE);
            MatchMember m1 = member(300L, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            MatchMember m2 = member(301L, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            when(memberMapper.selectList(any())).thenReturn(List.of(leader, m1, m2));

            matchGroupService.dissolveGroup(USER, GROUP_ID);

            verify(groupMapper).update(any(), any());
            verify(matchEventProducer).publishSquadEvent(eq(300L), eq(GROUP_ID), anyString(), anyString());
            verify(matchEventProducer).publishSquadEvent(eq(301L), eq(GROUP_ID), anyString(), anyString());
        }

        @Test
        @DisplayName("非组长解散：403 WISH_GROUP_LEADER_REQUIRED")
        void dissolveByNonLeader() {
            when(groupMapper.selectById(GROUP_ID)).thenReturn(group(MatchGroupStatus.OPEN, 2, 4));
            when(memberMapper.selectList(any())).thenReturn(
                    List.of(member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)));

            assertThatThrownBy(() -> matchGroupService.dissolveGroup(USER, GROUP_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_GROUP_LEADER_REQUIRED);
        }
    }

    // ---------------- 推荐 / 提醒 ----------------

    @Nested
    @DisplayName("匹配推荐与互相提醒")
    class RecommendAndRemind {

        @Test
        @DisplayName("推荐去重：排除本人已 ACTIVE 加入的组（验收：不重复推荐）")
        void recommendExcludesJoined() {
            MatchGroup joined = group(MatchGroupStatus.OPEN, 1, 4);
            joined.setId(1L);
            MatchGroup other = group(MatchGroupStatus.OPEN, 1, 4);
            other.setId(2L);
            other.setKeyword("看极光");
            when(groupMapper.selectList(any())).thenReturn(List.of(joined, other));
            MatchMember joinedMember = member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            joinedMember.setGroupId(1L);
            when(memberMapper.selectList(any())).thenReturn(List.of(joinedMember));

            var page = matchGroupService.recommendGroups(USER,
                    new MatchRecommendQuery("看极光", null, null, 10));

            assertThat(page.records()).extracting(MatchGroupVO::groupId).containsExactly(2L);
        }

        @Test
        @DisplayName("冷启动：无标签用户精确关键词也能拿到推荐（关键词优先）")
        void recommendColdStartByKeyword() {
            MatchGroup cold = group(MatchGroupStatus.OPEN, 1, 4);
            cold.setId(5L);
            cold.setKeyword("看极光");
            when(groupMapper.selectList(any())).thenReturn(List.of(cold));
            when(memberMapper.selectList(any())).thenReturn(List.of());

            var page = matchGroupService.recommendGroups(USER,
                    new MatchRecommendQuery("看极光", null, null, 10));

            assertThat(page.records()).hasSize(1);
            assertThat(page.records().get(0).matchScore()).isGreaterThan(0.0);
            assertThat(page.records().get(0).matchReason()).contains("看极光");
        }

        @Test
        @DisplayName("互相提醒：指定目标必达；发送者日限频 429")
        void remindSpecifiedTargetAndRateLimit() {
            MatchGroup open = group(MatchGroupStatus.OPEN, 2, 4);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(open);
            when(memberMapper.selectList(any()))
                    .thenReturn(List.of(member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)))
                    .thenReturn(List.of(
                            member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE),
                            member(300L, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE)));

            matchGroupService.remindMembers(USER, GROUP_ID, 300L);
            verify(matchEventProducer).publishSquadRemind(eq(300L), eq(GROUP_ID), eq("看极光"), any());

            // 限频：当日第 4 次
            when(valueOperations.increment(anyString())).thenReturn(4L);
            assertThatThrownBy(() -> matchGroupService.remindMembers(USER, GROUP_ID, 300L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
        }

        @Test
        @DisplayName("互相提醒：全部目标都活跃（无 idle）时提示无需提醒")
        void remindNoIdleMembers() {
            MatchGroup open = group(MatchGroupStatus.OPEN, 2, 4);
            when(groupMapper.selectById(GROUP_ID)).thenReturn(open);
            MatchMember sender = member(USER, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            MatchMember active = member(300L, MatchMemberRole.MEMBER, MatchMemberStatus.ACTIVE);
            when(memberMapper.selectList(any()))
                    .thenReturn(List.of(sender))
                    .thenReturn(List.of(sender, active));
            com.cloudmart.wish.entity.WishUserStat stat = new com.cloudmart.wish.entity.WishUserStat();
            stat.setUserId(300L);
            stat.setLastActiveAt(LocalDateTime.now());
            when(userStatMapper.selectBatchIds(any())).thenReturn(List.of(stat));

            assertThatThrownBy(() -> matchGroupService.remindMembers(USER, GROUP_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }
}

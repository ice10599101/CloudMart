package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.BottleCommentRequest;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.entity.DriftBottle;
import com.cloudmart.wish.entity.DriftBottleComment;
import com.cloudmart.wish.entity.DriftBottleFishLog;
import com.cloudmart.wish.entity.DriftBottleInteraction;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.DriftBottleStatus;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.mq.EncounterEventProducer;
import com.cloudmart.wish.repository.DriftBottleCommentMapper;
import com.cloudmart.wish.repository.DriftBottleFishLogMapper;
import com.cloudmart.wish.repository.DriftBottleInteractionMapper;
import com.cloudmart.wish.repository.DriftBottleMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.UserStatService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DriftBottleServiceImpl 单元测试")
class DriftBottleServiceImplTest {

    @Mock
    private DriftBottleMapper bottleMapper;
    @Mock
    private DriftBottleFishLogMapper fishLogMapper;
    @Mock
    private DriftBottleInteractionMapper interactionMapper;
    @Mock
    private DriftBottleCommentMapper commentMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private EncounterEventProducer encounterEventProducer;
    @Mock
    private UserFeignClient userFeignClient;

    private DriftBottleServiceImpl driftBottleService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long BOTTLE_ID = 2001L;
    private static final Long WISH_ID = 9091L;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DriftBottle.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottleFishLog.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottleInteraction.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottleComment.class);
        TableInfoHelper.initTableInfo(assistant, Wish.class);
    }

    @BeforeEach
    void setUp() {
        driftBottleService = new DriftBottleServiceImpl(
                bottleMapper, fishLogMapper, interactionMapper, commentMapper, wishMapper,
                userStatService, encounterEventProducer, userFeignClient);
    }

    private DriftBottle buildBottle(DriftBottleStatus status, Long pickerUserId, Long wishId) {
        DriftBottle bottle = new DriftBottle();
        bottle.setId(BOTTLE_ID);
        bottle.setThrowerUserId(OTHER_USER_ID);
        bottle.setContent(null);
        bottle.setWishId(wishId);
        bottle.setWishTitle(wishId == null ? null : "去海边看一次日出");
        bottle.setWishTags(wishId == null ? null : "[\"旅行\"]");
        bottle.setStatus(status);
        bottle.setPickerUserId(pickerUserId);
        bottle.setThrownAt(LocalDateTime.now().minusHours(1));
        bottle.setPickedAt(status == DriftBottleStatus.PICKED ? LocalDateTime.now() : null);
        bottle.setIsCollected(false);
        bottle.setReturnCount(0);
        bottle.setIsHidden(false);
        return bottle;
    }

    private Wish buildPublicWish(Long userId) {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(userId);
        wish.setTitle("去海边看一次日出");
        wish.setTags("[\"旅行\"]");
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setIsVisible(true);
        return wish;
    }

    private DriftBottleComment buildComment(Long id, Long bottleId, Long userId,
                                            Long parentId, Boolean isAnonymous) {
        DriftBottleComment comment = new DriftBottleComment();
        comment.setId(id);
        comment.setBottleId(bottleId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(parentId == null ? null : OTHER_USER_ID);
        comment.setContent("评论文本" + id);
        comment.setIsAnonymous(isAnonymous);
        comment.setCreatedAt(LocalDateTime.now());
        return comment;
    }

    private Map<String, Object> buildUserMap(Long id, String nickname) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", id);
        user.put("nickname", nickname);
        user.put("avatar", "http://cdn/avatar/" + id + ".png");
        return user;
    }

    // ========== getQuota ==========

    @Nested
    @DisplayName("getQuota - 每日配额查询")
    class QuotaQueryTests {

        @Test
        @DisplayName("返回投瓶/打捞计数与上限")
        void getQuota_success() {
            when(bottleMapper.selectCount(any())).thenReturn(3L);
            when(fishLogMapper.selectCount(any())).thenReturn(7L);

            var quota = driftBottleService.getQuota(USER_ID);

            assertThat(quota.throwUsed()).isEqualTo(3L);
            assertThat(quota.throwLimit()).isEqualTo(10);
            assertThat(quota.fishUsed()).isEqualTo(7L);
            assertThat(quota.fishLimit()).isEqualTo(20);
        }
    }

    // ========== throwBottle ==========

    @Nested
    @DisplayName("throwBottle - 投瓶")
    class ThrowBottleTests {

        @Test
        @DisplayName("content 与 wishId 都为空 → WISH_VALIDATION_ERROR")
        void throwBottle_bothEmpty_rejected() {
            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            verify(bottleMapper, never()).insert(any(DriftBottle.class));
        }

        @Test
        @DisplayName("content 与 wishId 同时提供 → WISH_VALIDATION_ERROR（二选一）")
        void throwBottle_bothSet_rejected() {
            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest("匿名文字", null, WISH_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("今日投瓶已达 10 个上限 → WISH_RATE_LIMITED")
        void throwBottle_dailyQuotaExceeded_429() {
            when(bottleMapper.selectCount(any())).thenReturn(10L);

            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest("匿名文字", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
            verify(bottleMapper, never()).insert(any(DriftBottle.class));
        }

        @Test
        @DisplayName("自由文字投瓶成功：role=THROWN、status=FLOATING、无 wish 快照")
        void throwBottle_freeText_success() {
            when(bottleMapper.selectCount(any())).thenReturn(0L);
            when(bottleMapper.insert(any(DriftBottle.class))).thenAnswer(inv -> {
                inv.getArgument(0, DriftBottle.class).setId(BOTTLE_ID);
                return 1;
            });

            var vo = driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(" 给未来的自己一句话 ", null, null));

            assertThat(vo.bottleId()).isEqualTo(BOTTLE_ID);
            assertThat(vo.role()).isEqualTo("THROWN");
            assertThat(vo.status()).isEqualTo("FLOATING");
            assertThat(vo.content()).isEqualTo("给未来的自己一句话");
            assertThat(vo.wishId()).isNull();
            assertThat(vo.wishTitle()).isNull();
        }

        @Test
        @DisplayName("关联心愿非本人/非公开/非进行中 → WISH_VALIDATION_ERROR")
        void throwBottle_wishNotApplicable_rejected() {
            when(bottleMapper.selectCount(any())).thenReturn(0L);
            when(wishMapper.selectById(WISH_ID)).thenReturn(
                    buildPublicWish(OTHER_USER_ID));

            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(null, null, WISH_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            verify(bottleMapper, never()).insert(any(DriftBottle.class));
        }

        @Test
        @DisplayName("关联本人公开心愿投瓶成功：快照 wishTitle/wishTags")
        void throwBottle_wishLinked_success() {
            when(bottleMapper.selectCount(any())).thenReturn(0L);
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildPublicWish(USER_ID));
            when(bottleMapper.insert(any(DriftBottle.class))).thenAnswer(inv -> {
                inv.getArgument(0, DriftBottle.class).setId(BOTTLE_ID);
                return 1;
            });

            var vo = driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(null, null, WISH_ID));

            assertThat(vo.wishId()).isEqualTo(WISH_ID);
            assertThat(vo.wishTitle()).isEqualTo("去海边看一次日出");
            assertThat(vo.wishTags()).containsExactly("旅行");
            assertThat(vo.content()).isNull();
        }
    }

    // ========== listCandidateWishes ==========

    @Nested
    @DisplayName("listCandidateWishes - 可关联心愿候选（近 30 条）")
    class CandidateWishesTests {

        @Test
        @DisplayName("返回本人可关联心愿 VO（id/标题/标签），保持查询顺序")
        void listCandidateWishes_success() {
            Wish w1 = buildPublicWish(USER_ID);
            w1.setId(11L);
            w1.setTitle("心愿甲");
            w1.setTags("[\"学习\"]");
            Wish w2 = buildPublicWish(USER_ID);
            w2.setId(12L);
            w2.setTitle("心愿乙");
            when(wishMapper.selectList(any())).thenReturn(List.of(w2, w1));

            var result = driftBottleService.listCandidateWishes(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).satisfies(v -> {
                assertThat(v.wishId()).isEqualTo(12L);
                assertThat(v.title()).isEqualTo("心愿乙");
            });
            assertThat(result.get(1)).satisfies(v -> {
                assertThat(v.wishId()).isEqualTo(11L);
                assertThat(v.title()).isEqualTo("心愿甲");
                assertThat(v.tags()).containsExactly("学习");
            });
        }

        @Test
        @DisplayName("无可关联心愿 → 返回空列表")
        void listCandidateWishes_empty() {
            when(wishMapper.selectList(any())).thenReturn(List.of());

            assertThat(driftBottleService.listCandidateWishes(USER_ID)).isEmpty();
        }
    }

    // ========== fishBottle ==========

    @Nested
    @DisplayName("fishBottle - 捞瓶")
    class FishBottleTests {

        @Test
        @DisplayName("海里无瓶 → 返回 null")
        void fishBottle_emptySea_returnsNull() {
            when(bottleMapper.selectList(any())).thenReturn(List.of());

            assertThat(driftBottleService.fishBottle(USER_ID)).isNull();
        }

        @Test
        @DisplayName("今日打捞已达 20 次上限 → WISH_RATE_LIMITED")
        void fishBottle_dailyQuotaExceeded_429() {
            when(fishLogMapper.selectCount(any())).thenReturn(20L);

            assertThatThrownBy(() -> driftBottleService.fishBottle(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
            verify(bottleMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("捞瓶成功：乐观更新命中 → role=PICKED，打捞流水落库")
        void fishBottle_success() {
            when(bottleMapper.selectList(any()))
                    .thenReturn(List.of(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID)));
            when(bottleMapper.update(isNull(), any())).thenReturn(1);

            var vo = driftBottleService.fishBottle(USER_ID);

            assertThat(vo).isNotNull();
            assertThat(vo.role()).isEqualTo("PICKED");
            assertThat(vo.status()).isEqualTo("PICKED");
            ArgumentCaptor<DriftBottleFishLog> logCaptor = ArgumentCaptor.forClass(DriftBottleFishLog.class);
            verify(fishLogMapper).insert(logCaptor.capture());
            assertThat(logCaptor.getValue().getBottleId()).isEqualTo(BOTTLE_ID);
            assertThat(logCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("并发被抢：第一次 CAS 未命中 → 重试第二次成功")
        void fishBottle_concurrentLost_retries() {
            when(bottleMapper.selectList(any()))
                    .thenReturn(List.of(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID)),
                            List.of(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID)));
            when(bottleMapper.update(isNull(), any())).thenReturn(0, 1);

            var vo = driftBottleService.fishBottle(USER_ID);

            assertThat(vo).isNotNull();
            assertThat(vo.role()).isEqualTo("PICKED");
            verify(bottleMapper, times(2)).update(isNull(), any());
        }

        @Test
        @DisplayName("连续 3 次 CAS 失败 → 返回 null")
        void fishBottle_allAttemptsFail_returnsNull() {
            when(bottleMapper.selectList(any()))
                    .thenReturn(List.of(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID)));
            when(bottleMapper.update(isNull(), any())).thenReturn(0);

            assertThat(driftBottleService.fishBottle(USER_ID)).isNull();
            verify(bottleMapper, times(3)).update(isNull(), any());
        }
    }

    // ========== listMine ==========

    @Nested
    @DisplayName("listMine - 我的漂流瓶")
    class ListMineTests {

        @Test
        @DisplayName("合并投出与捞到并按 bottleId 倒序")
        void listMine_mergesAndSorts() {
            DriftBottle thrown = buildBottle(DriftBottleStatus.FLOATING, null, null);
            thrown.setId(10L);
            thrown.setThrowerUserId(USER_ID);
            thrown.setContent("我投的");
            DriftBottle picked = buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID);
            picked.setId(20L);
            picked.setThrowerUserId(OTHER_USER_ID);

            when(bottleMapper.selectList(any()))
                    .thenReturn(List.of(thrown), List.of(picked));

            var result = driftBottleService.listMine(USER_ID);

            assertThat(result).extracting("role").containsExactly("PICKED", "THROWN");
            assertThat(result.get(0).bottleId()).isEqualTo(20L);
            assertThat(result.get(1).bottleId()).isEqualTo(10L);
        }
    }

    // ========== listCollected ==========

    @Nested
    @DisplayName("listCollected - 我收藏的漂流瓶")
    class ListCollectedTests {

        @Test
        @DisplayName("仅返回我捞起且已收藏的漂流瓶（role=PICKED，脱敏规则同 listMine）")
        void listCollected_success() {
            DriftBottle collected = buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID);
            collected.setIsCollected(true);
            when(bottleMapper.selectList(any())).thenReturn(List.of(collected));

            var result = driftBottleService.listCollected(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).role()).isEqualTo("PICKED");
            assertThat(result.get(0).isCollected()).isTrue();
            assertThat(result.get(0).bottleId()).isEqualTo(BOTTLE_ID);
        }

        @Test
        @DisplayName("未收藏 → 空列表")
        void listCollected_empty() {
            when(bottleMapper.selectList(any())).thenReturn(List.of());

            assertThat(driftBottleService.listCollected(USER_ID)).isEmpty();
        }
    }

    // ========== returnBottle ==========

    @Nested
    @DisplayName("returnBottle - 扔回海里")
    class ReturnBottleTests {

        @Test
        @DisplayName("投瓶人（参与者但非捞起人）扔回海里 → WISH_FORBIDDEN")
        void returnBottle_notPicker_forbidden() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.PICKED, OTHER_USER_ID, WISH_ID);
            bottle.setThrowerUserId(USER_ID);
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(bottle);

            assertThatThrownBy(() -> driftBottleService.returnBottle(USER_ID, BOTTLE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
            verify(bottleMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("非 PICKED 状态（已扔回海里）→ WISH_STATUS_CONFLICT")
        void returnBottle_notPicked_conflict() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.RETURNED, USER_ID, WISH_ID));

            assertThatThrownBy(() -> driftBottleService.returnBottle(USER_ID, BOTTLE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT);
        }

        @Test
        @DisplayName("扔回海里成功：条件更新命中（RETURNED + 清捞起人 + 回流次数+1）")
        void returnBottle_success() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(bottleMapper.update(isNull(), any())).thenReturn(1);

            assertThatCode(() -> driftBottleService.returnBottle(USER_ID, BOTTLE_ID))
                    .doesNotThrowAnyException();
            verify(bottleMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("并发下条件更新未命中 → WISH_STATUS_CONFLICT")
        void returnBottle_casMiss_conflict() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(bottleMapper.update(isNull(), any())).thenReturn(0);

            assertThatThrownBy(() -> driftBottleService.returnBottle(USER_ID, BOTTLE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT);
        }
    }

    // ========== collectBottle / updatePickerAnonymity ==========

    @Nested
    @DisplayName("collectBottle / updatePickerAnonymity - 收藏与捞瓶匿名开关")
    class PickerActionTests {

        @Test
        @DisplayName("投瓶人（参与者但非捞起人）收藏 → WISH_FORBIDDEN")
        void collectBottle_notPicker_forbidden() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.PICKED, OTHER_USER_ID, WISH_ID);
            bottle.setThrowerUserId(USER_ID);
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(bottle);

            assertThatThrownBy(() -> driftBottleService.collectBottle(USER_ID, BOTTLE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
        }

        @Test
        @DisplayName("收藏成功：isCollected 置 true（幂等：重复收藏也返回现状）")
        void collectBottle_success() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(bottleMapper.update(isNull(), any())).thenReturn(1);

            var vo = driftBottleService.collectBottle(USER_ID, BOTTLE_ID);

            assertThat(vo.isCollected()).isTrue();
            assertThat(vo.role()).isEqualTo("PICKED");
        }

        @Test
        @DisplayName("投瓶人（参与者但非捞起人）切换匿名 → WISH_FORBIDDEN")
        void updatePickerAnonymity_notPicker_forbidden() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.PICKED, OTHER_USER_ID, WISH_ID);
            bottle.setThrowerUserId(USER_ID);
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(bottle);

            assertThatThrownBy(() -> driftBottleService.updatePickerAnonymity(USER_ID, BOTTLE_ID, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
        }

        @Test
        @DisplayName("切实名成功：VO 透出捞瓶人身份（pickerUserId 非空）")
        void updatePickerAnonymity_realName_exposesPicker() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID);
            bottle.setThrowerUserId(OTHER_USER_ID);
            when(bottleMapper.selectById(BOTTLE_ID)).thenReturn(bottle);
            when(bottleMapper.update(isNull(), any())).thenReturn(1);
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(buildUserMap(USER_ID, "捞瓶旅人"))));

            var vo = driftBottleService.updatePickerAnonymity(USER_ID, BOTTLE_ID, false);

            assertThat(vo.pickerIsAnonymous()).isFalse();
            assertThat(vo.pickerUserId()).isEqualTo(USER_ID);
            assertThat(vo.pickerNickname()).isEqualTo("捞瓶旅人");
        }

        @Test
        @DisplayName("匿名捞瓶：listMine 中 VO 不透出捞瓶人身份（pickerUserId 为 null，无 Feign 查询）")
        void pickerAnonymous_maskedInListMine() {
            DriftBottle bottle = buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID);
            bottle.setThrowerUserId(OTHER_USER_ID);
            bottle.setPickerIsAnonymous(true);
            when(bottleMapper.selectList(any()))
                    .thenReturn(List.of(), List.of(bottle));

            var result = driftBottleService.listMine(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).pickerIsAnonymous()).isTrue();
            assertThat(result.get(0).pickerUserId()).isNull();
            assertThat(result.get(0).pickerNickname()).isNull();
            verify(userFeignClient, never()).batchGetUsers(any());
        }
    }

    // ========== interact ==========

    @Nested
    @DisplayName("interact - 匿名回应")
    class InteractTests {

        @Test
        @DisplayName("非本人捞起的漂流瓶 → WISH_NOT_FOUND")
        void interact_notPicker_404() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, OTHER_USER_ID, WISH_ID));

            assertThatThrownBy(() -> driftBottleService.interact(USER_ID, BOTTLE_ID, "BLESS"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }

        @Test
        @DisplayName("文字漂流瓶（无关联心愿）不可回应 → WISH_VALIDATION_ERROR")
        void interact_textBottle_rejected() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, null));

            assertThatThrownBy(() -> driftBottleService.interact(USER_ID, BOTTLE_ID, "BLESS"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("非法回应类型 → WISH_VALIDATION_ERROR")
        void interact_invalidType_rejected() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));

            assertThatThrownBy(() -> driftBottleService.interact(USER_ID, BOTTLE_ID, "HUG"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("BLESS 成功：不扣星光、不点亮、通知投瓶人（非点亮）")
        void interact_bless_success() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(interactionMapper.insert(any(DriftBottleInteraction.class))).thenReturn(1);

            var vo = driftBottleService.interact(USER_ID, BOTTLE_ID, "BLESS");

            assertThat(vo.role()).isEqualTo("PICKED");
            verify(userStatService, never()).spendStarlight(any(), any(Integer.class), any(), any());
            verify(wishMapper, never()).update(isNull(), any());
            verify(encounterEventProducer).publishBottleInteraction(OTHER_USER_ID, false);
        }

        @Test
        @DisplayName("LIGHT 成功：扣星光 2 + 对方心愿 light_count+1 + 通知投瓶人（点亮）")
        void interact_light_success() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(interactionMapper.insert(any(DriftBottleInteraction.class))).thenReturn(1);
            when(userStatService.spendStarlight(USER_ID, 2, ResourceLogSource.LIGHT_OTHER, BOTTLE_ID))
                    .thenReturn(8);
            when(wishMapper.update(isNull(), any())).thenReturn(1);

            driftBottleService.interact(USER_ID, BOTTLE_ID, "LIGHT");

            verify(userStatService).spendStarlight(USER_ID, 2, ResourceLogSource.LIGHT_OTHER, BOTTLE_ID);
            verify(wishMapper).update(isNull(), any());
            verify(encounterEventProducer).publishBottleInteraction(OTHER_USER_ID, true);
        }

        @Test
        @DisplayName("当日重复回应 → 唯一键冲突 → WISH_RATE_LIMITED")
        void interact_duplicate_429() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(interactionMapper.insert(any(DriftBottleInteraction.class)))
                    .thenThrow(new DuplicateKeyException("dup"));

            assertThatThrownBy(() -> driftBottleService.interact(USER_ID, BOTTLE_ID, "BLESS"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
            verify(userStatService, never()).spendStarlight(any(), any(Integer.class), any(), any());
            verify(encounterEventProducer, never()).publishBottleInteraction(any(), any(boolean.class));
        }
    }

    // ========== listComments ==========

    @Nested
    @DisplayName("listComments - 瓶下评论列表")
    class ListCommentsTests {

        @Test
        @DisplayName("非投瓶人/捞起人 → WISH_NOT_FOUND（防存在性探测）")
        void listComments_notViewer_404() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID));

            assertThatThrownBy(() -> driftBottleService.listComments(USER_ID, BOTTLE_ID, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
            verify(commentMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("匿名评论隐藏身份：userId=null、昵称=匿名瓶友、无 Feign 查询")
        void listComments_anonymousMasked() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(commentMapper.selectList(any())).thenReturn(List.of(
                    buildComment(1L, BOTTLE_ID, OTHER_USER_ID, null, true)));

            var page = driftBottleService.listComments(USER_ID, BOTTLE_ID, null, null);

            assertThat(page.records()).hasSize(1);
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();
            var vo = page.records().get(0);
            assertThat(vo.userId()).isNull();
            assertThat(vo.nickname()).isEqualTo("匿名瓶友");
            assertThat(vo.avatar()).isNull();
            assertThat(vo.isAnonymous()).isTrue();
            verify(userFeignClient, never()).batchGetUsers(any());
        }

        @Test
        @DisplayName("实名评论透出身份（Feign 昵称头像）；匿名父评论回复对象显示「匿名瓶友」")
        void listComments_realNameExposesIdentity() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            DriftBottleComment reply = buildComment(2L, BOTTLE_ID, USER_ID, 1L, false);
            DriftBottleComment parent = buildComment(1L, BOTTLE_ID, OTHER_USER_ID, null, true);
            when(commentMapper.selectList(any())).thenReturn(List.of(reply));
            when(commentMapper.selectBatchIds(any())).thenReturn(List.of(parent));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(buildUserMap(USER_ID, "心愿旅人甲"))));

            var page = driftBottleService.listComments(USER_ID, BOTTLE_ID, null, null);

            var vo = page.records().get(0);
            assertThat(vo.userId()).isEqualTo(USER_ID);
            assertThat(vo.nickname()).isEqualTo("心愿旅人甲");
            assertThat(vo.avatar()).isEqualTo("http://cdn/avatar/1001.png");
            assertThat(vo.isAnonymous()).isFalse();
            assertThat(vo.replyToNickname()).isEqualTo("匿名瓶友");
        }

        @Test
        @DisplayName("分页：多取 1 条探测 hasMore，nextCursor 为末条评论 ID")
        void listComments_cursorPagination() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            List<DriftBottleComment> fetched = new ArrayList<>();
            for (long i = 1; i <= 3; i++) {
                fetched.add(buildComment(i, BOTTLE_ID, OTHER_USER_ID, null, true));
            }
            when(commentMapper.selectList(any())).thenReturn(fetched);

            var page = driftBottleService.listComments(USER_ID, BOTTLE_ID, null, 2);

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isEqualTo(String.valueOf(page.records().get(1).id()));
        }

        @Test
        @DisplayName("非法游标 → WISH_VALIDATION_ERROR")
        void listComments_invalidCursor_rejected() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));

            assertThatThrownBy(() -> driftBottleService.listComments(USER_ID, BOTTLE_ID, "abc", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    // ========== addComment ==========

    @Nested
    @DisplayName("addComment - 发表评论/回复")
    class AddCommentTests {

        @Test
        @DisplayName("非投瓶人/捞起人 → WISH_NOT_FOUND")
        void addComment_notViewer_404() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID));

            assertThatThrownBy(() -> driftBottleService.addComment(USER_ID, BOTTLE_ID,
                    new BottleCommentRequest("你好", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
            verify(commentMapper, never()).insert(any(DriftBottleComment.class));
        }

        @Test
        @DisplayName("顶级评论默认匿名：身份隐藏、去除首尾空白、无 Feign 查询")
        void addComment_defaultAnonymous() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(commentMapper.insert(any(DriftBottleComment.class))).thenAnswer(inv -> {
                inv.getArgument(0, DriftBottleComment.class).setId(1L);
                return 1;
            });

            var vo = driftBottleService.addComment(USER_ID, BOTTLE_ID,
                    new BottleCommentRequest(" 你好呀 ", null, null));

            assertThat(vo.id()).isEqualTo(1L);
            assertThat(vo.userId()).isNull();
            assertThat(vo.nickname()).isEqualTo("匿名瓶友");
            assertThat(vo.avatar()).isNull();
            assertThat(vo.isAnonymous()).isTrue();
            assertThat(vo.parentId()).isNull();
            assertThat(vo.replyToNickname()).isNull();
            assertThat(vo.content()).isEqualTo("你好呀");
            verify(userFeignClient, never()).batchGetUsers(any());
        }

        @Test
        @DisplayName("回复评论：replyToUserId 由父评论推导；匿名父评论回复对象显示「匿名瓶友」")
        void addComment_replyDerivesFromParent() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(commentMapper.selectById(10L))
                    .thenReturn(buildComment(10L, BOTTLE_ID, OTHER_USER_ID, null, true));
            when(commentMapper.insert(any(DriftBottleComment.class))).thenAnswer(inv -> {
                inv.getArgument(0, DriftBottleComment.class).setId(11L);
                return 1;
            });
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(buildUserMap(USER_ID, "心愿旅人甲"))));

            var vo = driftBottleService.addComment(USER_ID, BOTTLE_ID,
                    new BottleCommentRequest("回复你", 10L, false));

            ArgumentCaptor<DriftBottleComment> captor = ArgumentCaptor.forClass(DriftBottleComment.class);
            verify(commentMapper).insert(captor.capture());
            assertThat(captor.getValue().getReplyToUserId()).isEqualTo(OTHER_USER_ID);
            assertThat(captor.getValue().getParentId()).isEqualTo(10L);
            assertThat(captor.getValue().getIsAnonymous()).isFalse();

            assertThat(vo.parentId()).isEqualTo(10L);
            assertThat(vo.replyToNickname()).isEqualTo("匿名瓶友");
            assertThat(vo.isAnonymous()).isFalse();
            assertThat(vo.userId()).isEqualTo(USER_ID);
            assertThat(vo.nickname()).isEqualTo("心愿旅人甲");
        }

        @Test
        @DisplayName("父评论不存在 → WISH_NOT_FOUND")
        void addComment_parentMissing_404() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(commentMapper.selectById(10L)).thenReturn(null);

            assertThatThrownBy(() -> driftBottleService.addComment(USER_ID, BOTTLE_ID,
                    new BottleCommentRequest("回复你", 10L, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
            verify(commentMapper, never()).insert(any(DriftBottleComment.class));
        }

        @Test
        @DisplayName("父评论属于其他漂流瓶 → WISH_NOT_FOUND")
        void addComment_parentOtherBottle_404() {
            when(bottleMapper.selectById(BOTTLE_ID))
                    .thenReturn(buildBottle(DriftBottleStatus.PICKED, USER_ID, WISH_ID));
            when(commentMapper.selectById(10L))
                    .thenReturn(buildComment(10L, 999L, OTHER_USER_ID, null, true));

            assertThatThrownBy(() -> driftBottleService.addComment(USER_ID, BOTTLE_ID,
                    new BottleCommentRequest("回复你", 10L, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
            verify(commentMapper, never()).insert(any(DriftBottleComment.class));
        }
    }
}

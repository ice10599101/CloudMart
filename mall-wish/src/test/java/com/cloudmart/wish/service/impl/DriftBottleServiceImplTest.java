package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.entity.DriftBottle;
import com.cloudmart.wish.entity.DriftBottleInteraction;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.DriftBottleStatus;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.mq.EncounterEventProducer;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private DriftBottleInteractionMapper interactionMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private EncounterEventProducer encounterEventProducer;

    private DriftBottleServiceImpl driftBottleService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long BOTTLE_ID = 2001L;
    private static final Long WISH_ID = 9091L;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DriftBottle.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottleInteraction.class);
        TableInfoHelper.initTableInfo(assistant, Wish.class);
    }

    @BeforeEach
    void setUp() {
        driftBottleService = new DriftBottleServiceImpl(
                bottleMapper, interactionMapper, wishMapper, userStatService, encounterEventProducer);
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

    // ========== throwBottle ==========

    @Nested
    @DisplayName("throwBottle - 投瓶")
    class ThrowBottleTests {

        @Test
        @DisplayName("content 与 wishId 都为空 → WISH_VALIDATION_ERROR")
        void throwBottle_bothEmpty_rejected() {
            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            verify(bottleMapper, never()).insert(any(DriftBottle.class));
        }

        @Test
        @DisplayName("content 与 wishId 同时提供 → WISH_VALIDATION_ERROR（二选一）")
        void throwBottle_bothSet_rejected() {
            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest("匿名文字", WISH_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("自由文字投瓶成功：role=THROWN、status=FLOATING、无 wish 快照")
        void throwBottle_freeText_success() {
            when(bottleMapper.insert(any(DriftBottle.class))).thenAnswer(inv -> {
                inv.getArgument(0, DriftBottle.class).setId(BOTTLE_ID);
                return 1;
            });

            var vo = driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(" 给未来的自己一句话 ", null));

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
            when(wishMapper.selectById(WISH_ID)).thenReturn(
                    buildPublicWish(OTHER_USER_ID));

            assertThatThrownBy(() -> driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(null, WISH_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            verify(bottleMapper, never()).insert(any(DriftBottle.class));
        }

        @Test
        @DisplayName("关联本人公开心愿投瓶成功：快照 wishTitle/wishTags")
        void throwBottle_wishLinked_success() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildPublicWish(USER_ID));
            when(bottleMapper.insert(any(DriftBottle.class))).thenAnswer(inv -> {
                inv.getArgument(0, DriftBottle.class).setId(BOTTLE_ID);
                return 1;
            });

            var vo = driftBottleService.throwBottle(USER_ID,
                    new ThrowBottleRequest(null, WISH_ID));

            assertThat(vo.wishId()).isEqualTo(WISH_ID);
            assertThat(vo.wishTitle()).isEqualTo("去海边看一次日出");
            assertThat(vo.wishTags()).containsExactly("旅行");
            assertThat(vo.content()).isNull();
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
        @DisplayName("捞瓶成功：乐观更新命中 → role=PICKED")
        void fishBottle_success() {
            when(bottleMapper.selectList(any()))
                    .thenReturn(List.of(buildBottle(DriftBottleStatus.FLOATING, null, WISH_ID)));
            when(bottleMapper.update(isNull(), any())).thenReturn(1);

            var vo = driftBottleService.fishBottle(USER_ID);

            assertThat(vo).isNotNull();
            assertThat(vo.role()).isEqualTo("PICKED");
            assertThat(vo.status()).isEqualTo("PICKED");
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
}
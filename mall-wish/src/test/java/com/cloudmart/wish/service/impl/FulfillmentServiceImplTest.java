package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.SubmitFulfillmentRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.entity.WishFulfillment;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishFulfillmentMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.UserStatService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FulfillmentServiceImpl 单元测试")
class FulfillmentServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishFulfillmentMapper wishFulfillmentMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private UserFeignClient userFeignClient;

    private WishContentSanitizer contentSanitizer;

    @InjectMocks
    private FulfillmentServiceImpl fulfillmentService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long WISH_ID = 2001L;
    private static final Long FULFILLMENT_ID = 3001L;
    private static final int STARLIGHT_REWARD = 50;

    @BeforeAll
    static void initEntityMeta() {
        // LambdaUpdateWrapper.set(SFunction) 构造期解析列名需要 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Wish.class);
    }

    @BeforeEach
    void setUp() {
        contentSanitizer = new WishContentSanitizer(List.of());
        fulfillmentService = new FulfillmentServiceImpl(
                wishMapper, wishFulfillmentMapper, userStatService, userFeignClient, contentSanitizer
        );
    }

    // ========== submitFulfillment ==========

    @Nested
    @DisplayName("submitFulfillment - 提交还愿")
    class SubmitFulfillmentTests {

        @Test
        @DisplayName("作者对 ACTIVE 心愿还愿成功：FULFILLED+BLOOM+50星光+徽章联动")
        void submitFulfillment_activeWish_success() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.insert(any(WishFulfillment.class))).thenAnswer(invocation -> {
                WishFulfillment fulfillment = invocation.getArgument(0);
                fulfillment.setId(FULFILLMENT_ID);
                fulfillment.setCreatedAt(LocalDateTime.now());
                return 1;
            });
            when(wishMapper.update(any(), any())).thenReturn(1);
            WishBadge badge = buildBadge(9001L, "初次结果");
            when(userStatService.incrementOnFulfilled(USER_ID)).thenReturn(List.of(badge));
            when(userStatService.earnStarlight(eq(USER_ID), eq(STARLIGHT_REWARD),
                    eq(ResourceLogSource.FULFILL), eq(FULFILLMENT_ID))).thenReturn(STARLIGHT_REWARD);

            var result = fulfillmentService.submitFulfillment(USER_ID, WISH_ID, buildRequest());

            assertThat(result.id()).isEqualTo(FULFILLMENT_ID);
            assertThat(result.wishId()).isEqualTo(WISH_ID);
            assertThat(result.status()).isEqualTo(WishStatus.FULFILLED);
            assertThat(result.fruitType()).isEqualTo(FruitType.BLOOM);
            assertThat(result.starlightReward()).isEqualTo(STARLIGHT_REWARD);
            assertThat(result.badgeAwarded()).hasSize(1);
            assertThat(result.badgeAwarded().get(0).id()).isEqualTo(9001L);
            assertThat(result.badgeAwarded().get(0).name()).isEqualTo("初次结果");
            verify(wishFulfillmentMapper).insert(any(WishFulfillment.class));
            verify(userStatService).incrementOnFulfilled(USER_ID);
            verify(userStatService).earnStarlight(USER_ID, STARLIGHT_REWARD,
                    ResourceLogSource.FULFILL, FULFILLMENT_ID);
        }

        @Test
        @DisplayName("OVERDUE 心愿同样可还愿（逾期补结果语义）")
        void submitFulfillment_overdueWish_success() {
            Wish wish = buildWish(WishStatus.OVERDUE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.insert(any(WishFulfillment.class))).thenAnswer(invocation -> {
                WishFulfillment fulfillment = invocation.getArgument(0);
                fulfillment.setId(FULFILLMENT_ID);
                fulfillment.setCreatedAt(LocalDateTime.now());
                return 1;
            });
            when(wishMapper.update(any(), any())).thenReturn(1);
            when(userStatService.incrementOnFulfilled(USER_ID)).thenReturn(Collections.emptyList());
            when(userStatService.earnStarlight(anyLong(), anyInt(), any(), any())).thenReturn(STARLIGHT_REWARD);

            var result = fulfillmentService.submitFulfillment(USER_ID, WISH_ID, buildRequest());

            assertThat(result.status()).isEqualTo(WishStatus.FULFILLED);
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void submitFulfillment_wishNotFound_throws() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(USER_ID, WISH_ID, buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_NOT_FOUND));

            verify(wishFulfillmentMapper, never()).insert(any(WishFulfillment.class));
        }

        @Test
        @DisplayName("非作者还愿抛出 WISH_NOT_AUTHOR")
        void submitFulfillment_notAuthor_throws() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(OTHER_USER_ID, WISH_ID, buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR));

            verify(wishFulfillmentMapper, never()).insert(any(WishFulfillment.class));
        }

        @Test
        @DisplayName("PRIVATE 心愿对非作者抛出 WISH_NOT_FOUND（防存在性探测）")
        void submitFulfillment_privateWishNonAuthor_throwsNotFound() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(OTHER_USER_ID, WISH_ID, buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_NOT_FOUND));
        }

        @Test
        @DisplayName("FULFILLED 心愿重复还愿抛出 WISH_NOT_FULFILLABLE")
        void submitFulfillment_alreadyFulfilled_throws() {
            Wish wish = buildWish(WishStatus.FULFILLED);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(USER_ID, WISH_ID, buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_NOT_FULFILLABLE));

            verify(wishFulfillmentMapper, never()).insert(any(WishFulfillment.class));
        }

        @Test
        @DisplayName("并发场景状态条件 UPDATE 未命中抛出 WISH_NOT_FULFILLABLE（事务回滚）")
        void submitFulfillment_concurrentStateChange_throws() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.insert(any(WishFulfillment.class))).thenReturn(1);
            // 模拟并发：查询后状态已被其他请求流转
            when(wishMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(USER_ID, WISH_ID, buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_NOT_FULFILLABLE));

            verify(userStatService, never()).incrementOnFulfilled(anyLong());
            verify(userStatService, never()).earnStarlight(anyLong(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("还愿故事含路径穿越片段抛出 WISH_VALIDATION_ERROR")
        void submitFulfillment_pathTraversal_throws() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            SubmitFulfillmentRequest request = new SubmitFulfillmentRequest(
                    "看 ../etc/passwd", null, null);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(USER_ID, WISH_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));

            verify(wishFulfillmentMapper, never()).insert(any(WishFulfillment.class));
        }

        @Test
        @DisplayName("故事与感悟入库前完成 XSS 转义，audit_status=PENDING 先发后审")
        void submitFulfillment_escapesHtmlAndMarksPending() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.update(any(), any())).thenReturn(1);
            when(userStatService.incrementOnFulfilled(USER_ID)).thenReturn(Collections.emptyList());
            when(userStatService.earnStarlight(anyLong(), anyInt(), any(), any())).thenReturn(STARLIGHT_REWARD);

            SubmitFulfillmentRequest request = new SubmitFulfillmentRequest(
                    "<script>alert('x')</script>", List.of("oss://key1.png"), "<b>感悟</b>");

            fulfillmentService.submitFulfillment(USER_ID, WISH_ID, request);

            verify(wishFulfillmentMapper).insert(org.mockito.ArgumentMatchers.<WishFulfillment>argThat(f ->
                    f.getStory().contains("&lt;script&gt;")
                            && !f.getStory().contains("<script>")
                            && f.getFeeling().contains("&lt;b&gt;")
                            && f.getAuditStatus() == AuditStatus.PENDING
                            && Boolean.TRUE.equals(f.getIsVisible())
                            && f.getMediaUrls().contains("oss://key1.png")
            ));
        }
    }

    // ========== getFulfillmentDetail ==========

    @Nested
    @DisplayName("getFulfillmentDetail - 还愿详情")
    class GetFulfillmentDetailTests {

        @Test
        @DisplayName("公开心愿匿名查看还愿详情成功（Feign 正常）")
        void getFulfillmentDetail_publicAnonymous_success() {
            Wish wish = buildWish(WishStatus.FULFILLED);
            wish.setFruitType(FruitType.BLOOM);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.selectOne(any())).thenReturn(buildFulfillment());
            when(userFeignClient.batchGetUsers(any())).thenReturn(buildUserResponse(USER_ID, "小星", "avatar.png"));

            var result = fulfillmentService.getFulfillmentDetail(WISH_ID, null);

            assertThat(result.id()).isEqualTo(FULFILLMENT_ID);
            assertThat(result.wishId()).isEqualTo(WISH_ID);
            assertThat(result.story()).isEqualTo("终于上岸了！");
            assertThat(result.mediaUrls()).containsExactly("oss://key1.png", "oss://key2.png");
            assertThat(result.feeling()).isEqualTo("感恩一切");
            assertThat(result.authorId()).isEqualTo(USER_ID);
            assertThat(result.authorNickname()).isEqualTo("小星");
            assertThat(result.authorAvatar()).isEqualTo("avatar.png");
        }

        @Test
        @DisplayName("Feign 失败降级为占位作者信息（不阻塞详情）")
        void getFulfillmentDetail_feignFallback_placeholderAuthor() {
            Wish wish = buildWish(WishStatus.FULFILLED);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.selectOne(any())).thenReturn(buildFulfillment());
            when(userFeignClient.batchGetUsers(any())).thenThrow(new RuntimeException("feign timeout"));

            var result = fulfillmentService.getFulfillmentDetail(WISH_ID, null);

            assertThat(result.authorNickname()).isEqualTo("心愿旅人");
            assertThat(result.authorAvatar()).isEqualTo("");
        }

        @Test
        @DisplayName("心愿未还愿抛出 WISH_FULFILLMENT_NOT_FOUND")
        void getFulfillmentDetail_notFulfilled_throws() {
            Wish wish = buildWish(WishStatus.ACTIVE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> fulfillmentService.getFulfillmentDetail(WISH_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_FULFILLMENT_NOT_FOUND));
        }

        @Test
        @DisplayName("还愿故事不可见（is_visible=false）抛出 WISH_FULFILLMENT_NOT_FOUND")
        void getFulfillmentDetail_invisible_throws() {
            Wish wish = buildWish(WishStatus.FULFILLED);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            WishFulfillment fulfillment = buildFulfillment();
            fulfillment.setIsVisible(false);
            when(wishFulfillmentMapper.selectOne(any())).thenReturn(fulfillment);

            assertThatThrownBy(() -> fulfillmentService.getFulfillmentDetail(WISH_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_FULFILLMENT_NOT_FOUND));
        }

        @Test
        @DisplayName("PRIVATE 心愿非作者查看还愿抛出 WISH_NOT_FOUND（防存在性探测）")
        void getFulfillmentDetail_privateNonAuthor_throwsNotFound() {
            Wish wish = buildWish(WishStatus.FULFILLED);
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> fulfillmentService.getFulfillmentDetail(WISH_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_NOT_FOUND));

            verify(wishFulfillmentMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("PRIVATE 心愿作者本人可查看还愿详情")
        void getFulfillmentDetail_privateAuthor_success() {
            Wish wish = buildWish(WishStatus.FULFILLED);
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishFulfillmentMapper.selectOne(any())).thenReturn(buildFulfillment());
            when(userFeignClient.batchGetUsers(any())).thenReturn(buildUserResponse(USER_ID, "小星", "avatar.png"));

            var result = fulfillmentService.getFulfillmentDetail(WISH_ID, USER_ID);

            assertThat(result.id()).isEqualTo(FULFILLMENT_ID);
        }
    }

    // ========== 构造工具 ==========

    private Wish buildWish(WishStatus status) {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(USER_ID);
        wish.setTitle("考研上岸");
        wish.setStatus(status);
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setIsVisible(true);
        wish.setFruitType(FruitType.GLOW);
        return wish;
    }

    private SubmitFulfillmentRequest buildRequest() {
        return new SubmitFulfillmentRequest("终于上岸了！", List.of("oss://key1.png"), "感恩一切");
    }

    private WishFulfillment buildFulfillment() {
        WishFulfillment fulfillment = new WishFulfillment();
        fulfillment.setId(FULFILLMENT_ID);
        fulfillment.setWishId(WISH_ID);
        fulfillment.setUserId(USER_ID);
        fulfillment.setStory("终于上岸了！");
        fulfillment.setMediaUrls("[\"oss://key1.png\",\"oss://key2.png\"]");
        fulfillment.setFeeling("感恩一切");
        fulfillment.setAuditStatus(AuditStatus.PENDING);
        fulfillment.setIsVisible(true);
        fulfillment.setIsInherited(false);
        fulfillment.setCreatedAt(LocalDateTime.now());
        return fulfillment;
    }

    private WishBadge buildBadge(Long id, String name) {
        WishBadge badge = new WishBadge();
        badge.setId(id);
        badge.setName(name);
        return badge;
    }

    private ApiResponse<List<Map<String, Object>>> buildUserResponse(Long userId, String nickname, String avatar) {
        Map<String, Object> user = Map.of(
                "id", userId,
                "nickname", nickname,
                "avatar", avatar
        );
        return ApiResponse.ok(new ArrayList<>(List.of(user)));
    }
}

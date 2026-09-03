package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.MyWishListQuery;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.dto.WishListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.entity.WishProgress;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.GrowthRecordType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.WishService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishServiceImpl 单元测试")
class WishServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishCategoryMapper wishCategoryMapper;
    @Mock
    private WishGrowthRecordMapper wishGrowthRecordMapper;
    @Mock
    private WishProgressMapper wishProgressMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private com.cloudmart.wish.repository.WishCheckinMapper wishCheckinMapper;

    @InjectMocks
    private WishServiceImpl wishService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long WISH_ID = 2001L;
    private static final Long CATEGORY_ID = 100L;

    @BeforeEach
    void setUp() {
        wishService = new WishServiceImpl(
                wishMapper, wishCategoryMapper, wishCheckinMapper, wishGrowthRecordMapper,
                wishProgressMapper, userStatService, userFeignClient
        );
    }

    // ========== createWish ==========

    @Nested
    @DisplayName("createWish - 创建心愿")
    class CreateWishTests {

        @Test
        @DisplayName("正常创建 PUBLIC 心愿：enableAiReply=false, auditStrategy=LAZY")
        void createWish_public_success() {
            WishCategory category = buildCategory();
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(category);
            when(wishMapper.insert(any(Wish.class))).thenAnswer(invocation -> {
                Wish wish = invocation.getArgument(0);
                wish.setId(WISH_ID);
                wish.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            CreateWishRequest request = new CreateWishRequest(
                    "考研上岸", "我要考上研究生", List.of("url1"),
                    CATEGORY_ID, List.of("学习"),
                    WishVisibility.PUBLIC,
                    LocalDateTime.now().plusMonths(6), false, false
            , null, null);

            var result = wishService.createWish(USER_ID, request);

            assertThat(result.id()).isEqualTo(WISH_ID);
            assertThat(result.title()).isEqualTo("考研上岸");
            assertThat(result.status()).isEqualTo(WishStatus.ACTIVE);
            assertThat(result.fruitType()).isEqualTo(FruitType.GLOW);
            verify(userStatService).incrementOnWishCreated(USER_ID);
            verify(wishProgressMapper).insert(any(WishProgress.class));
        }

        @Test
        @DisplayName("创建 TREE_HOLE 心愿：enableAiReply=true, auditStrategy=STRICT")
        void createWish_treeHole_enablesAiAndStrictAudit() {
            WishCategory category = buildCategory();
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(category);
            when(wishMapper.insert(any(Wish.class))).thenAnswer(invocation -> {
                Wish wish = invocation.getArgument(0);
                wish.setId(WISH_ID);
                wish.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            CreateWishRequest request = new CreateWishRequest(
                    "树洞秘密", "说出来会好受些", null,
                    CATEGORY_ID, null,
                    WishVisibility.TREE_HOLE,
                    null, false, false
            , null, null);

            wishService.createWish(USER_ID, request);

            verify(wishMapper).insert(org.mockito.ArgumentMatchers.<Wish>argThat(w ->
                    w.getEnableAiReply() == true &&
                    w.getAuditStrategy() == AuditStrategy.STRICT &&
                    w.getTriggerEnvEmo() == true
            ));
        }

        @Test
        @DisplayName("创建 PUBLIC 心愿：insert 后固化球面坐标（上树）")
        void createWish_public_assignsTreePosition() {
            WishCategory category = buildCategory();
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(category);
            when(wishMapper.insert(any(Wish.class))).thenAnswer(invocation -> {
                Wish wish = invocation.getArgument(0);
                wish.setId(WISH_ID);
                wish.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            CreateWishRequest request = new CreateWishRequest(
                    "考研上岸", "我要考上研究生", List.of("url1"),
                    CATEGORY_ID, List.of("学习"),
                    WishVisibility.PUBLIC,
                    LocalDateTime.now().plusMonths(6), false, false
            , null, null);

            wishService.createWish(USER_ID, request);

            // 坐标固化：insert 后追加一次 updateById，theta/phi 非空且在球面值域
            verify(wishMapper).updateById(org.mockito.ArgumentMatchers.<Wish>argThat(w ->
                    w.getTreeTheta() != null && w.getTreePhi() != null
                            && w.getTreeTheta().doubleValue() >= 0
                            && w.getTreeTheta().doubleValue() < 2 * Math.PI
                            && w.getTreePhi().doubleValue() > 0
                            && w.getTreePhi().doubleValue() <= Math.PI
            ));
        }

        @Test
        @DisplayName("创建 TREE_HOLE 心愿：不上树不固化坐标")
        void createWish_treeHole_skipsTreePosition() {
            WishCategory category = buildCategory();
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(category);
            when(wishMapper.insert(any(Wish.class))).thenAnswer(invocation -> {
                Wish wish = invocation.getArgument(0);
                wish.setId(WISH_ID);
                wish.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            CreateWishRequest request = new CreateWishRequest(
                    "树洞秘密", "说出来会好受些", null,
                    CATEGORY_ID, null,
                    WishVisibility.TREE_HOLE,
                    null, false, false
            , null, null);

            wishService.createWish(USER_ID, request);

            verify(wishMapper, never()).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("分类不存在时抛出 WISH_CATEGORY_INVALID")
        void createWish_categoryNotFound_throwsException() {
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(null);

            CreateWishRequest request = new CreateWishRequest(
                    "标题", "描述", null,
                    CATEGORY_ID, null,
                    WishVisibility.PUBLIC,
                    null, false, false
            , null, null);

            assertThatThrownBy(() -> wishService.createWish(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_CATEGORY_INVALID);
                    });

            verify(wishMapper, never()).insert(any(Wish.class));
            verify(userStatService, never()).incrementOnWishCreated(anyLong());
        }
    }

    // ========== updateWish ==========

    @Nested
    @DisplayName("updateWish - 更新心愿")
    class UpdateWishTests {

        @Test
        @DisplayName("作者更新心愿标题成功")
        void updateWish_byAuthor_success() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);

            UpdateWishRequest request = new UpdateWishRequest(
                    "更新标题", null, null, null, null, null, null, null
            , null, null);

            var result = wishService.updateWish(USER_ID, WISH_ID, request);

            assertThat(result.id()).isEqualTo(WISH_ID);
            verify(wishMapper).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("非作者更新心愿抛出 WISH_NOT_AUTHOR")
        void updateWish_byNonAuthor_throwsException() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            UpdateWishRequest request = new UpdateWishRequest(
                    "恶意修改", null, null, null, null, null, null, null
            , null, null);

            assertThatThrownBy(() -> wishService.updateWish(OTHER_USER_ID, WISH_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);
                    });

            verify(wishMapper, never()).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void updateWish_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            UpdateWishRequest request = new UpdateWishRequest(
                    "标题", null, null, null, null, null, null, null
            , null, null);

            assertThatThrownBy(() -> wishService.updateWish(USER_ID, WISH_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("更新分类时校验新分类存在性")
        void updateWish_invalidCategory_throwsException() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishCategoryMapper.selectById(9999L)).thenReturn(null);

            UpdateWishRequest request = new UpdateWishRequest(
                    null, null, null, 9999L, null, null, null, null
            , null, null);

            assertThatThrownBy(() -> wishService.updateWish(USER_ID, WISH_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_CATEGORY_INVALID);
                    });
        }

        @Test
        @DisplayName("PRIVATE 转 PUBLIC：转公开时固化球面坐标（随同次 updateById 落库）")
        void updateWish_privateToPublic_assignsTreePosition() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            wish.setVisibility(WishVisibility.PRIVATE);
            wish.setTreeTheta(null);
            wish.setTreePhi(null);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);

            UpdateWishRequest request = new UpdateWishRequest(
                    null, null, null, null, null, WishVisibility.PUBLIC, null, null
            , null, null);

            wishService.updateWish(USER_ID, WISH_ID, request);

            verify(wishMapper).updateById(org.mockito.ArgumentMatchers.<Wish>argThat(w ->
                    w.getVisibility() == WishVisibility.PUBLIC
                            && w.getTreeTheta() != null && w.getTreePhi() != null
            ));
        }

        @Test
        @DisplayName("已有坐标的 PUBLIC 心愿再更新：坐标不重算（一经写入不变更）")
        void updateWish_publicWithExistingPosition_keepsPosition() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            java.math.BigDecimal originalTheta = java.math.BigDecimal.valueOf(3.1415926);
            java.math.BigDecimal originalPhi = java.math.BigDecimal.valueOf(1.5707963);
            wish.setTreeTheta(originalTheta);
            wish.setTreePhi(originalPhi);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);

            UpdateWishRequest request = new UpdateWishRequest(
                    "新标题", null, null, null, null, null, null, null
            , null, null);

            wishService.updateWish(USER_ID, WISH_ID, request);

            verify(wishMapper).updateById(org.mockito.ArgumentMatchers.<Wish>argThat(w ->
                    originalTheta.compareTo(w.getTreeTheta()) == 0
                            && originalPhi.compareTo(w.getTreePhi()) == 0
            ));
        }
    }

    // ========== deleteWish ==========

    @Nested
    @DisplayName("deleteWish - 软删心愿")
    class DeleteWishTests {

        @Test
        @DisplayName("作者软删心愿成功，同事务调用 decrementOnWishDeleted")
        void deleteWish_byAuthor_success() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.deleteById(WISH_ID)).thenReturn(1);

            var result = wishService.deleteWish(USER_ID, WISH_ID);

            assertThat(result.id()).isEqualTo(WISH_ID);
            assertThat(result.deletedAt()).isNotNull();
            verify(userStatService).decrementOnWishDeleted(USER_ID);
        }

        @Test
        @DisplayName("非作者删除抛出 WISH_NOT_AUTHOR")
        void deleteWish_byNonAuthor_throwsException() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> wishService.deleteWish(OTHER_USER_ID, WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);
                    });

            verify(wishMapper, never()).deleteById(anyLong());
            verify(userStatService, never()).decrementOnWishDeleted(anyLong());
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void deleteWish_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> wishService.deleteWish(USER_ID, WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }
    }

    // ========== getWishDetail ==========

    @Nested
    @DisplayName("getWishDetail - 心愿详情")
    class GetWishDetailTests {

        @Test
        @DisplayName("作者查看 PRIVATE 心愿成功")
        void getWishDetail_authorViewsPrivate_success() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(buildCategory());
            when(wishProgressMapper.selectById(WISH_ID)).thenReturn(buildProgress());
            when(wishGrowthRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", USER_ID, "nickname", "测试用户", "avatar", "avatar.png")
                    )));

            var result = wishService.getWishDetail(WISH_ID, USER_ID);

            assertThat(result.id()).isEqualTo(WISH_ID);
            assertThat(result.authorNickname()).isEqualTo("测试用户");
            assertThat(result.visibility()).isEqualTo(WishVisibility.PRIVATE);
        }

        @Test
        @DisplayName("非作者查看 PRIVATE 心愿返回 404（不暴露存在性）")
        void getWishDetail_nonAuthorViewsPrivate_throws404() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            wish.setVisibility(WishVisibility.PRIVATE);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> wishService.getWishDetail(WISH_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("心愿不存在返回 404")
        void getWishDetail_notFound_throws404() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> wishService.getWishDetail(WISH_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("未登录用户查看 PUBLIC + APPROVED 心愿成功")
        void getWishDetail_anonymousViewsPublicApproved_success() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            wish.setVisibility(WishVisibility.PUBLIC);
            wish.setAuditStatus(AuditStatus.APPROVED);
            wish.setIsVisible(true);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishCategoryMapper.selectById(CATEGORY_ID)).thenReturn(buildCategory());
            when(wishProgressMapper.selectById(WISH_ID)).thenReturn(buildProgress());
            when(wishGrowthRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", USER_ID, "nickname", "作者", "avatar", "a.png")
                    )));

            var result = wishService.getWishDetail(WISH_ID, null);

            assertThat(result.id()).isEqualTo(WISH_ID);
            assertThat(result.authorNickname()).isEqualTo("作者");
        }

        @Test
        @DisplayName("未登录用户查看 REJECTED 心愿返回 404")
        void getWishDetail_anonymousViewsRejected_throws404() {
            Wish wish = buildWish();
            wish.setUserId(USER_ID);
            wish.setVisibility(WishVisibility.PUBLIC);
            wish.setAuditStatus(AuditStatus.REJECTED);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            assertThatThrownBy(() -> wishService.getWishDetail(WISH_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }
    }

    // ========== listWishes ==========

    @Nested
    @DisplayName("listWishes - 心愿列表 cursor 分页")
    class ListWishesTests {

        @Test
        @DisplayName("首页查询返回正确分页结果和 nextCursor")
        void listWishes_firstPage_success() {
            List<Wish> wishes = new ArrayList<>();
            for (int i = 5; i >= 1; i--) {
                wishes.add(buildWishWithId((long) i));
            }
            when(wishMapper.selectList(any())).thenReturn(wishes);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", USER_ID, "nickname", "用户", "avatar", "a.png")
                    )));

            WishListQuery query = new WishListQuery(null, null, null, null, null, 5);
            var result = wishService.listWishes(query);

            assertThat(result.records()).hasSize(5);
            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("有更多数据时返回 hasMore=true 和 nextCursor")
        void listWishes_hasMore_success() {
            List<Wish> wishes = new ArrayList<>();
            for (int i = 6; i >= 1; i--) {
                wishes.add(buildWishWithId((long) i));
            }
            when(wishMapper.selectList(any())).thenReturn(wishes);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", USER_ID, "nickname", "用户", "avatar", "a.png")
                    )));

            WishListQuery query = new WishListQuery(null, null, null, null, null, 5);
            var result = wishService.listWishes(query);

            assertThat(result.records()).hasSize(5);
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isEqualTo("2");
        }

        @Test
        @DisplayName("空结果返回空列表")
        void listWishes_emptyResult_returnsEmptyList() {
            when(wishMapper.selectList(any())).thenReturn(Collections.emptyList());

            WishListQuery query = new WishListQuery(null, null, null, null, null, 20);
            var result = wishService.listWishes(query);

            assertThat(result.records()).isEmpty();
            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("无效 cursor 抛出 WISH_VALIDATION_ERROR")
        void listWishes_invalidCursor_throwsException() {
            WishListQuery query = new WishListQuery(null, null, null, null, "abc", 20);

            assertThatThrownBy(() -> wishService.listWishes(query))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
                    });
        }
    }

    // ========== listMyWishes ==========

    @Nested
    @DisplayName("listMyWishes - 我的心愿列表")
    class ListMyWishesTests {

        @Test
        @DisplayName("按状态筛选返回我的心愿列表")
        void listMyWishes_filterByStatus_success() {
            List<Wish> wishes = List.of(buildWishWithId(WISH_ID));
            when(wishMapper.selectList(any())).thenReturn(wishes);
            when(wishProgressMapper.selectBatchIds(any()))
                    .thenReturn(List.of(buildProgress()));

            MyWishListQuery query = new MyWishListQuery(WishStatus.ACTIVE, null, 20);
            var result = wishService.listMyWishes(USER_ID, query);

            assertThat(result.records()).hasSize(1);
            assertThat(result.records().get(0).id()).isEqualTo(WISH_ID);
            assertThat(result.records().get(0).progress()).isEqualTo(50);
        }

        @Test
        @DisplayName("无心愿时返回空列表")
        void listMyWishes_noWishes_returnsEmptyList() {
            when(wishMapper.selectList(any())).thenReturn(Collections.emptyList());

            MyWishListQuery query = new MyWishListQuery(null, null, 20);
            var result = wishService.listMyWishes(USER_ID, query);

            assertThat(result.records()).isEmpty();
        }
    }

    // ========== Helper methods ==========

    private WishCategory buildCategory() {
        WishCategory category = new WishCategory();
        category.setId(CATEGORY_ID);
        category.setCode("study");
        category.setName("学习成长");
        category.setSort(1);
        return category;
    }

    private Wish buildWish() {
        return buildWishWithId(WISH_ID);
    }

    private Wish buildWishWithId(Long id) {
        Wish wish = new Wish();
        wish.setId(id);
        wish.setUserId(USER_ID);
        wish.setTitle("测试心愿");
        wish.setDescription("测试描述");
        wish.setCategoryId(CATEGORY_ID);
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setEnableAiReply(false);
        wish.setAuditStrategy(AuditStrategy.LAZY);
        wish.setTriggerEnvEmo(false);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setFruitType(FruitType.GLOW);
        wish.setMediaUrls("[\"url1\"]");
        wish.setTags("[\"学习\"]");
        wish.setSameWishCount(0);
        wish.setLightCount(0);
        wish.setBlessCount(0);
        wish.setSupportCount(0);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setIsVisible(true);
        wish.setCreatedAt(LocalDateTime.now());
        wish.setUpdatedAt(LocalDateTime.now());
        return wish;
    }

    private WishProgress buildProgress() {
        WishProgress progress = new WishProgress();
        progress.setWishId(WISH_ID);
        progress.setCurrentValue(50);
        progress.setTargetValue(100);
        progress.setCurrentStreak(0);
        progress.setMaxStreak(0);
        progress.setVersion(1);
        return progress;
    }

    // ========== scanOverdueWishes ==========

    @Nested
    @DisplayName("scanOverdueWishes - OVERDUE 状态机扫描")
    class ScanOverdueTests {

        @BeforeAll
        static void initWishEntityMeta() {
            // LambdaQueryWrapper.select(SFunction) / LambdaUpdateWrapper.set(SFunction)
            // 构造期解析列名需要 TableInfo 缓存
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, Wish.class);
        }

        @Test
        @DisplayName("无过期心愿：返回 0 且不执行 UPDATE")
        void noExpired_returnsZero() {
            when(wishMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertThat(wishService.scanOverdueWishes()).isZero();
            verify(wishMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("两条过期心愿：流转为 OVERDUE 并返回计数")
        void expiredBatch_transferred() {
            when(wishMapper.selectList(any())).thenReturn(List.of(
                    buildWishWithId(3001L), buildWishWithId(3002L)));
            when(wishMapper.update(any(), any())).thenReturn(2);

            int transferred = wishService.scanOverdueWishes();

            assertThat(transferred).isEqualTo(2);
            verify(wishMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        }

        @Test
        @DisplayName("超过单批 500 条：分两批流转累计 502")
        void multiBatch_accumulates() {
            List<Wish> fullBatch = new ArrayList<>();
            for (long i = 1; i <= 500; i++) {
                fullBatch.add(buildWishWithId(i));
            }
            when(wishMapper.selectList(any()))
                    .thenReturn(fullBatch)
                    .thenReturn(List.of(buildWishWithId(501L), buildWishWithId(502L)));
            when(wishMapper.update(any(), any())).thenReturn(500, 2);

            assertThat(wishService.scanOverdueWishes()).isEqualTo(502);
            verify(wishMapper, org.mockito.Mockito.times(2)).update(any(), any());
        }
    }

    // ========== sparkWish（星火永久收藏，文档 2.3） ==========

    @Nested
    @DisplayName("sparkWish - 设为星火永久收藏")
    class SparkWishTests {

        @BeforeAll
        static void initWishEntityMeta() {
            // LambdaUpdateWrapper.set(SFunction) 构造期解析列名需要 TableInfo 缓存
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, Wish.class);
        }

        private Wish buildFulfilledWish() {
            Wish wish = buildWish();
            wish.setStatus(WishStatus.FULFILLED);
            wish.setFruitType(FruitType.BLOOM);
            return wish;
        }

        @Test
        @DisplayName("已还愿心愿设为星火：BLOOM→SPARK 条件 UPDATE 成功")
        void spark_success() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildFulfilledWish());
            when(wishMapper.update(any(), any())).thenReturn(1);

            var result = wishService.sparkWish(USER_ID, WISH_ID);

            assertThat(result.id()).isEqualTo(WISH_ID);
            assertThat(result.fruitType()).isEqualTo(FruitType.SPARK);
            verify(wishMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        }

        @Test
        @DisplayName("幂等：已是 SPARK 直接返回成功，不执行 UPDATE")
        void spark_alreadySpark_idempotent() {
            Wish sparkWish = buildFulfilledWish();
            sparkWish.setFruitType(FruitType.SPARK);
            when(wishMapper.selectById(WISH_ID)).thenReturn(sparkWish);

            var result = wishService.sparkWish(USER_ID, WISH_ID);

            assertThat(result.fruitType()).isEqualTo(FruitType.SPARK);
            verify(wishMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("未还愿心愿（ACTIVE）设置星火：409 WISH_NOT_FULFILLED")
        void spark_notFulfilled_rejected() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildWish());

            assertThatThrownBy(() -> wishService.sparkWish(USER_ID, WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FULFILLED);
            verify(wishMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("非作者设置星火：403 WISH_NOT_AUTHOR")
        void spark_notAuthor_rejected() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildFulfilledWish());

            assertThatThrownBy(() -> wishService.sparkWish(OTHER_USER_ID, WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);
            verify(wishMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("并发未命中但已是 SPARK：幂等成功")
        void spark_concurrentAlreadySpark_idempotent() {
            Wish sparkWish = buildFulfilledWish();
            sparkWish.setFruitType(FruitType.SPARK);
            // 第一次 selectById 返回 BLOOM 快照，条件 UPDATE 未命中，重查已 SPARK
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildFulfilledWish(), sparkWish);
            when(wishMapper.update(any(), any())).thenReturn(0);

            var result = wishService.sparkWish(USER_ID, WISH_ID);

            assertThat(result.fruitType()).isEqualTo(FruitType.SPARK);
        }

        @Test
        @DisplayName("并发未命中且非 SPARK（状态漂移）：409 WISH_SPARK_CONFLICT")
        void spark_concurrentStateDrift_conflict() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(buildFulfilledWish());
            when(wishMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> wishService.sparkWish(USER_ID, WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.WISH_SPARK_CONFLICT);
        }
    }
}

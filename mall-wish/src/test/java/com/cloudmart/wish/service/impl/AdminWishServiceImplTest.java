package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishCheckinMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminWishServiceImpl 单元测试")
class AdminWishServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishCategoryMapper wishCategoryMapper;
    @Mock
    private WishCheckinMapper wishCheckinMapper;
    @Mock
    private WishInteractionMapper wishInteractionMapper;

    @InjectMocks
    private AdminWishServiceImpl adminWishService;

    private static final Long WISH_ID = 2001L;
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 100L;

    @BeforeEach
    void setUp() {
        adminWishService = new AdminWishServiceImpl(wishMapper, wishCategoryMapper,
                wishCheckinMapper, wishInteractionMapper);
    }

    @Nested
    @DisplayName("listWishes - 管理后台心愿列表")
    class ListWishesTests {

        @Test
        @DisplayName("分页查询返回正确总数和记录")
        void listWishes_success() {
            Page<Wish> page = new Page<>(1, 20);
            page.setTotal(1);
            page.setRecords(List.of(buildWish()));
            when(wishMapper.selectPage(any(Page.class), any())).thenReturn(page);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            AdminWishListQuery query = new AdminWishListQuery(null, null, null, null, null, null, 1, 20);
            var result = adminWishService.listWishes(query);

            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).categoryName()).isEqualTo("学习成长");
        }

        @Test
        @DisplayName("空结果返回空列表")
        void listWishes_emptyResult() {
            Page<Wish> page = new Page<>(1, 20);
            page.setTotal(0);
            page.setRecords(List.of());
            when(wishMapper.selectPage(any(Page.class), any())).thenReturn(page);

            AdminWishListQuery query = new AdminWishListQuery(null, null, null, null, null, null, 1, 20);
            var result = adminWishService.listWishes(query);

            assertThat(result.getTotal()).isEqualTo(0);
            assertThat(result.getRecords()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getWishDetail - 管理后台心愿详情")
    class GetWishDetailTests {

        @Test
        @DisplayName("正常返回心愿详情（含审核字段）")
        void getWishDetail_success() {
            Wish wish = buildWish();
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            var result = adminWishService.getWishDetail(WISH_ID);

            assertThat(result.id()).isEqualTo(WISH_ID);
            assertThat(result.auditStatus()).isEqualTo(AuditStatus.PENDING);
            assertThat(result.categoryName()).isEqualTo("学习成长");
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void getWishDetail_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminWishService.getWishDetail(WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("auditWish - 审核心愿")
    class AuditWishTests {

        @Test
        @DisplayName("PENDING → APPROVED：is_visible 设为 true")
        void auditWish_approve_success() {
            Wish wish = buildWish();
            wish.setAuditStatus(AuditStatus.PENDING);
            Wish approvedWish = buildWish();
            approvedWish.setAuditStatus(AuditStatus.APPROVED);
            approvedWish.setIsVisible(true);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, approvedWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            AdminAuditWishRequest request = new AdminAuditWishRequest(AuditStatus.APPROVED, null);
            var result = adminWishService.auditWish(WISH_ID, request);

            assertThat(result.auditStatus()).isEqualTo(AuditStatus.APPROVED);
            verify(wishMapper).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("PENDING → REJECTED：is_visible 设为 false")
        void auditWish_reject_success() {
            Wish wish = buildWish();
            wish.setAuditStatus(AuditStatus.PENDING);
            Wish rejectedWish = buildWish();
            rejectedWish.setAuditStatus(AuditStatus.REJECTED);
            rejectedWish.setIsVisible(false);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, rejectedWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            AdminAuditWishRequest request = new AdminAuditWishRequest(AuditStatus.REJECTED, "内容违规");
            var result = adminWishService.auditWish(WISH_ID, request);

            assertThat(result.auditStatus()).isEqualTo(AuditStatus.REJECTED);
            verify(wishMapper).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("发布免审后的后置下架：APPROVED → REJECTED 流转成功")
        void auditWish_postModeration_reject_success() {
            Wish wish = buildWish();
            wish.setAuditStatus(AuditStatus.APPROVED);
            wish.setIsVisible(true);
            Wish rejectedWish = buildWish();
            rejectedWish.setAuditStatus(AuditStatus.REJECTED);
            rejectedWish.setIsVisible(false);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, rejectedWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            AdminAuditWishRequest request = new AdminAuditWishRequest(AuditStatus.REJECTED, "内容违规");
            var result = adminWishService.auditWish(WISH_ID, request);

            assertThat(result.auditStatus()).isEqualTo(AuditStatus.REJECTED);
            verify(wishMapper).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("同审核状态重复操作抛出 WISH_STATUS_CONFLICT")
        void auditWish_sameStatus_throwsConflict() {
            Wish wish = buildWish();
            wish.setAuditStatus(AuditStatus.APPROVED);
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);

            AdminAuditWishRequest request = new AdminAuditWishRequest(AuditStatus.APPROVED, null);

            assertThatThrownBy(() -> adminWishService.auditWish(WISH_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT);
                    });

            verify(wishMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void auditWish_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            AdminAuditWishRequest request = new AdminAuditWishRequest(AuditStatus.APPROVED, null);

            assertThatThrownBy(() -> adminWishService.auditWish(WISH_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("updateVisibility - 上架/下架（对齐帖子管理模式）")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("下架成功：is_visible 更新为 false 并返回最新 VO")
        void updateVisibility_off_success() {
            Wish wish = buildWish();
            wish.setIsVisible(true);
            Wish offWish = buildWish();
            offWish.setIsVisible(false);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, offWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            var result = adminWishService.updateVisibility(WISH_ID, false);

            assertThat(result.isVisible()).isFalse();
            verify(wishMapper).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("上架成功：is_visible 更新为 true")
        void updateVisibility_on_success() {
            Wish wish = buildWish();
            wish.setIsVisible(false);
            Wish onWish = buildWish();
            onWish.setIsVisible(true);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, onWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            var result = adminWishService.updateVisibility(WISH_ID, true);

            assertThat(result.isVisible()).isTrue();
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void updateVisibility_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminWishService.updateVisibility(WISH_ID, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });

            verify(wishMapper, never()).updateById(any(Wish.class));
        }
    }

    @Nested
    @DisplayName("updateTop - 置顶/取消置顶（对齐帖子管理模式）")
    class UpdateTopTests {

        @Test
        @DisplayName("置顶成功：is_top 更新为 true")
        void updateTop_on_success() {
            Wish wish = buildWish();
            wish.setIsTop(false);
            Wish topWish = buildWish();
            topWish.setIsTop(true);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, topWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            var result = adminWishService.updateTop(WISH_ID, true);

            assertThat(result.isTop()).isTrue();
            verify(wishMapper).updateById(any(Wish.class));
        }

        @Test
        @DisplayName("取消置顶成功：is_top 更新为 false")
        void updateTop_off_success() {
            Wish wish = buildWish();
            wish.setIsTop(true);
            Wish normalWish = buildWish();
            normalWish.setIsTop(false);

            when(wishMapper.selectById(WISH_ID)).thenReturn(wish, normalWish);
            when(wishMapper.updateById(any(Wish.class))).thenReturn(1);
            when(wishCategoryMapper.selectBatchIds(any())).thenReturn(List.of(buildCategory()));

            var result = adminWishService.updateTop(WISH_ID, false);

            assertThat(result.isTop()).isFalse();
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void updateTop_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminWishService.updateTop(WISH_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("deleteWish - 删除心愿（软删）")
    class DeleteWishTests {

        @Test
        @DisplayName("删除成功：调用 deleteById（@TableLogic 软删）")
        void deleteWish_success() {
            Wish wish = buildWish();
            when(wishMapper.selectById(WISH_ID)).thenReturn(wish);
            when(wishMapper.deleteById(WISH_ID)).thenReturn(1);

            adminWishService.deleteWish(WISH_ID);

            verify(wishMapper).deleteById(WISH_ID);
        }

        @Test
        @DisplayName("心愿不存在抛出 WISH_NOT_FOUND")
        void deleteWish_notFound_throwsException() {
            when(wishMapper.selectById(WISH_ID)).thenReturn(null);

            assertThatThrownBy(() -> adminWishService.deleteWish(WISH_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
                    });

            verify(wishMapper, never()).deleteById(any(Long.class));
        }
    }

    // ========== Helper methods ==========

    private WishCategory buildCategory() {
        WishCategory category = new WishCategory();
        category.setId(CATEGORY_ID);
        category.setCode("study");
        category.setName("学习成长");
        return category;
    }

    private Wish buildWish() {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(USER_ID);
        wish.setTitle("测试心愿");
        wish.setDescription("测试描述");
        wish.setCategoryId(CATEGORY_ID);
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setFruitType(FruitType.GLOW);
        wish.setAuditStatus(AuditStatus.PENDING);
        wish.setAuditStrategy(AuditStrategy.LAZY);
        wish.setIsVisible(true);
        wish.setLightCount(0);
        wish.setSameWishCount(0);
        wish.setBlessCount(0);
        wish.setSupportCount(0);
        wish.setCreatedAt(LocalDateTime.now());
        wish.setUpdatedAt(LocalDateTime.now());
        return wish;
    }
}

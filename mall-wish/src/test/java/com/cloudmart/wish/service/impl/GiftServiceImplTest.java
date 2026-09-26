package com.cloudmart.wish.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.dto.AdminGiftRequest;
import com.cloudmart.wish.dto.SendGiftRequest;
import com.cloudmart.wish.entity.Gift;
import com.cloudmart.wish.entity.GiftRecord;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.feign.LiveFeignClient;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.GiftMapper;
import com.cloudmart.wish.repository.GiftRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.SendGiftResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GiftServiceImpl 送礼服务单元测试")
@ExtendWith(MockitoExtension.class)
class GiftServiceImplTest {

    private static final Long USER_ID = 10001L;

    @Mock
    private GiftMapper giftMapper;
    @Mock
    private GiftRecordMapper giftRecordMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private GiftRateLimiter giftRateLimiter;
    @Mock
    private CommunityFeignClient communityFeignClient;
    @Mock
    private LiveFeignClient liveFeignClient;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private GiftServiceImpl giftService;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.support.TransactionTemplate executorTx =
                org.mockito.Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class);
        org.mockito.Mockito.lenient().when(executorTx.execute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> ((org.springframework.transaction.support.TransactionCallback<Object>) inv.getArgument(0))
                        .doInTransaction(null));
        giftService = new GiftServiceImpl(giftMapper, giftRecordMapper, wishMapper,
                userStatService, giftRateLimiter, communityFeignClient, liveFeignClient,
                userFeignClient, transactionTemplate,
                new com.cloudmart.wish.policy.WishAccessPolicy(),
                new com.cloudmart.wish.service.impl.WishOperationExecutor(
                        org.mockito.Mockito.mock(com.cloudmart.wish.repository.WishOperationMapper.class), executorTx));
        // 事务模板直接执行回调（单元测试不依赖真实事务管理器）
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Integer>) invocation.getArgument(0))
                        .doInTransaction((TransactionStatus) null));
        lenient().when(giftRateLimiter.checkSendDailyLimit(anyLong())).thenReturn(true);
        lenient().when(userStatService.spendStarlight(anyLong(), anyInt(), any(), any())).thenReturn(123);
    }

        /** B03 基线：送礼目标必须公开可读（PUBLIC + APPROVED + isVisible） */
    private Wish publicWish(Long id, Long ownerId) {
        Wish wish = new Wish();
        wish.setId(id);
        wish.setUserId(ownerId);
        wish.setVisibility(com.cloudmart.wish.enums.WishVisibility.PUBLIC);
        wish.setAuditStatus(com.cloudmart.wish.enums.AuditStatus.APPROVED);
        wish.setIsVisible(true);
        return wish;
    }

private Gift onShelfGift(long id, String name, int price) {
        Gift gift = new Gift();
        gift.setId(id);
        gift.setName(name);
        gift.setPriceStarlight(price);
        gift.setStatus("ON_SHELF");
        gift.setSort(1);
        return gift;
    }

    private SendGiftRequest request(long giftId, int count, String targetType, long targetId) {
        return new SendGiftRequest(giftId, count, targetType, targetId, null);
    }

    @Nested
    @DisplayName("sendGift 送礼")
    class SendGiftTests {

        @Test
        @DisplayName("心愿场景成功 - 记录快照落库 + 按单价×数量扣星光 + 返回余额")
        void shouldSendGiftToWish() {
            when(giftMapper.selectById(1L)).thenReturn(onShelfGift(1L, "爱心", 5));
            Wish wish = publicWish(777L, 20002L);
            when(wishMapper.selectById(777L)).thenReturn(wish);

            SendGiftResultVO result = giftService.sendGift(USER_ID, request(1L, 3, "WISH", 777L), null);

            assertThat(result.totalPrice()).isEqualTo(15);
            assertThat(result.receiverId()).isEqualTo(20002L);
            assertThat(result.balanceAfter()).isEqualTo(123);
            assertThat(result.giftName()).isEqualTo("爱心");

            ArgumentCaptor<GiftRecord> recordCaptor = ArgumentCaptor.forClass(GiftRecord.class);
            verify(giftRecordMapper).insert(recordCaptor.capture());
            GiftRecord record = recordCaptor.getValue();
            assertThat(record.getGiftName()).isEqualTo("爱心");
            assertThat(record.getUnitPrice()).isEqualTo(5);
            assertThat(record.getCount()).isEqualTo(3);
            assertThat(record.getTotalPrice()).isEqualTo(15);
            assertThat(record.getSenderId()).isEqualTo(USER_ID);
            assertThat(record.getReceiverId()).isEqualTo(20002L);
            assertThat(record.getTargetType()).isEqualTo("WISH");

            verify(userStatService).spendStarlight(eq(USER_ID), eq(15), eq(ResourceLogSource.GIFT_SEND), any());
        }

        @Test
        @DisplayName("礼物不存在 - 抛 GIFT_NOT_FOUND 且不扣费")
        void shouldRejectUnknownGift() {
            when(giftMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(404L, 1, "WISH", 777L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("GIFT_NOT_FOUND");

            verify(giftRecordMapper, never()).insert(any(GiftRecord.class));
        }

        @Test
        @DisplayName("礼物已下架 - 抛 GIFT_OFF_SHELF 且不扣费")
        void shouldRejectOffShelfGift() {
            Gift gift = onShelfGift(1L, "爱心", 5);
            gift.setStatus("OFF_SHELF");
            when(giftMapper.selectById(1L)).thenReturn(gift);

            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(1L, 1, "WISH", 777L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("GIFT_OFF_SHELF");

            verify(userStatService, never()).spendStarlight(anyLong(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("送礼场景非法 - 抛 GIFT_TARGET_TYPE_INVALID")
        void shouldRejectInvalidTargetType() {
            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(1L, 1, "PRODUCT", 1L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("GIFT_TARGET_TYPE_INVALID");
        }

        @Test
        @DisplayName("心愿不存在 - 抛 GIFT_TARGET_NOT_FOUND 且不扣费")
        void shouldRejectMissingWishTarget() {
            when(giftMapper.selectById(1L)).thenReturn(onShelfGift(1L, "爱心", 5));
            when(wishMapper.selectById(777L)).thenReturn(null);

            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(1L, 1, "WISH", 777L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("GIFT_TARGET_NOT_FOUND");

            verify(giftRecordMapper, never()).insert(any(GiftRecord.class));
        }

        @Test
        @DisplayName("帖子场景 - 帖子归属由 mall-community 解析；服务不可用抛 503 不扣费")
        void shouldResolvePostOwnerViaCommunity() {
            when(giftMapper.selectById(1L)).thenReturn(onShelfGift(1L, "爱心", 5));
            when(communityFeignClient.getPostOwner(888L))
                    .thenReturn(ApiResponse.fail("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用"));

            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(1L, 1, "POST", 888L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("COMMUNITY_SERVICE_UNAVAILABLE");

            verify(giftRecordMapper, never()).insert(any(GiftRecord.class));
        }

        @Test
        @DisplayName("直播间场景 - 主播为收礼人，广播在事务体外执行")
        void shouldSendGiftToLiveRoomAndBroadcast() {
            when(giftMapper.selectById(1L)).thenReturn(onShelfGift(1L, "火箭", 199));
            when(liveFeignClient.getRoomOwner(9L))
                    .thenReturn(ApiResponse.ok(Map.of("roomId", 9L, "ownerId", 30003L)));

            SendGiftResultVO result = giftService.sendGift(USER_ID, request(1L, 2, "LIVE_ROOM", 9L), null);

            assertThat(result.receiverId()).isEqualTo(30003L);
            assertThat(result.totalPrice()).isEqualTo(398);
            verify(liveFeignClient).broadcastGiftNotice(eq(9L), any());
        }

        @Test
        @DisplayName("限频命中 - 抛 WISH_RATE_LIMITED 且不扣费")
        void shouldRejectWhenRateLimited() {
            when(giftMapper.selectById(1L)).thenReturn(onShelfGift(1L, "爱心", 5));
            Wish wish = publicWish(777L, 20002L);
            when(wishMapper.selectById(777L)).thenReturn(wish);
            when(giftRateLimiter.checkSendDailyLimit(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(1L, 1, "WISH", 777L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("WISH_RATE_LIMITED");
        }

        @Test
        @DisplayName("星光不足 - spendStarlight 抛 402 且事务体异常向外传播")
        void shouldPropagateInsufficientBalance() {
            when(giftMapper.selectById(1L)).thenReturn(onShelfGift(1L, "皇冠", 88));
            Wish wish = publicWish(777L, 20002L);
            when(wishMapper.selectById(777L)).thenReturn(wish);
            when(userStatService.spendStarlight(anyLong(), anyInt(), any(), any()))
                    .thenThrow(new BusinessException("WISH_STARLIGHT_INSUFFICIENT", "星光余额不足"));

            assertThatThrownBy(() -> giftService.sendGift(USER_ID, request(1L, 1, "WISH", 777L), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("WISH_STARLIGHT_INSUFFICIENT");
        }
    }

    @Nested
    @DisplayName("管理端目录管理")
    class AdminTests {

        @Test
        @DisplayName("新增礼物 - 默认未上架、排序默认 0")
        void shouldCreateGiftWithDefaults() {
            AdminGiftRequest req = new AdminGiftRequest("新礼物", null, null, 10, null, null, null);

            var vo = giftService.adminCreateGift(req);

            assertThat(vo.status()).isEqualTo("OFF_SHELF");
            assertThat(vo.sort()).isZero();
            verify(giftMapper).insert(any(Gift.class));
        }

        @Test
        @DisplayName("上架/下架切换")
        void shouldToggleStatus() {
            Gift gift = onShelfGift(1L, "爱心", 5);
            when(giftMapper.selectById(1L)).thenReturn(gift);

            var vo = giftService.adminUpdateGiftStatus(1L, false);

            assertThat(vo.status()).isEqualTo("OFF_SHELF");
            verify(giftMapper).updateById(gift);
        }

        @Test
        @DisplayName("删除不存在的礼物 - 抛 GIFT_NOT_FOUND")
        void shouldRejectDeleteUnknownGift() {
            when(giftMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> giftService.adminDeleteGift(404L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("GIFT_NOT_FOUND");
        }

        @Test
        @DisplayName("记录筛选场景非法 - 抛 GIFT_TARGET_TYPE_INVALID")
        void shouldRejectInvalidTypeInRecordQuery() {
            assertThatThrownBy(() -> giftService.adminListRecords(null, null, "BAD", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("GIFT_TARGET_TYPE_INVALID");
        }
    }

    @Test
    @DisplayName("资产总览 - 聚合送/收两方向件数与星光")
    void shouldAggregateMyGiftSummary() {
        Map<String, Object> sentRow = new HashMap<>();
        sentRow.put("total_count", new java.math.BigDecimal("5"));
        sentRow.put("total_price", 88L);
        Map<String, Object> receivedRow = new HashMap<>();
        receivedRow.put("total_count", 3);
        receivedRow.put("total_price", new java.math.BigDecimal("120"));

        when(giftRecordMapper.selectMaps(any())).thenReturn(List.of(sentRow), List.of(receivedRow));

        var summary = giftService.getMyGiftSummary(USER_ID);

        assertThat(summary.sentCount()).isEqualTo(5L);
        assertThat(summary.sentStarlight()).isEqualTo(88L);
        assertThat(summary.receivedCount()).isEqualTo(3L);
        assertThat(summary.receivedStarlight()).isEqualTo(120L);
    }

    @Test
    @DisplayName("资产总览 - 无记录时返回 0")
    void shouldReturnZeroSummaryWhenNoRecords() {
        when(giftRecordMapper.selectMaps(any())).thenReturn(List.of());

        var summary = giftService.getMyGiftSummary(USER_ID);

        assertThat(summary.sentCount()).isZero();
        assertThat(summary.sentStarlight()).isZero();
        assertThat(summary.receivedCount()).isZero();
        assertThat(summary.receivedStarlight()).isZero();
    }

    @Test
    @DisplayName("礼物目录 - 仅返回上架礼物")
    void shouldListOnlyOnShelfGifts() {
        Gift onShelf = onShelfGift(1L, "爱心", 5);
        Gift offShelf = onShelfGift(2L, "下架礼物", 6);
        offShelf.setStatus("OFF_SHELF");
        when(giftMapper.selectList(any())).thenReturn(List.of(onShelf));

        var list = giftService.listOnShelfGifts();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).name()).isEqualTo("爱心");
    }
}

package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.UserAsset;
import com.cloudmart.wish.entity.VirtualAsset;
import com.cloudmart.wish.entity.WishOperation;
import com.cloudmart.wish.enums.AssetPayMethod;
import com.cloudmart.wish.enums.AssetType;
import com.cloudmart.wish.repository.BrandMapper;
import com.cloudmart.wish.repository.BrandPoolMemberMapper;
import com.cloudmart.wish.repository.BrandPoolMapper;
import com.cloudmart.wish.repository.BrandRewardLogMapper;
import com.cloudmart.wish.repository.UserAssetMapper;
import com.cloudmart.wish.repository.WishOperationMapper;
import com.cloudmart.wish.repository.VirtualAssetMapper;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishUserBadgeMapper;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.ExchangeResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B05 兑换修复测试（T09 语义）：100/80、80/80 正确成功；
 * DB 条件扣库存、售罄不扣款、支付白名单、有效期；B04 幂等重放不二次扣款。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("资产兑换（B04/B05）")
class CollectionExchangeTest {

    private static final Long USER = 1001L;
    private static final Long ASSET = 900L;

    @Mock
    private VirtualAssetMapper assetMapper;
    @Mock
    private UserAssetMapper userAssetMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private BrandPoolMapper poolMapper;
    @Mock
    private BrandRewardLogMapper brandRewardLogMapper;
    @Mock
    private BrandPoolMemberMapper poolMemberMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private WishBadgeMapper badgeMapper;
    @Mock
    private WishUserBadgeMapper userBadgeMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private WishOperationMapper operationMapper;

    private CollectionServiceImpl collectionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        WishOperationExecutor executor = new WishOperationExecutor(operationMapper, txTemplate);
        collectionService = new CollectionServiceImpl(assetMapper, userAssetMapper, brandMapper,
                poolMapper, brandRewardLogMapper, poolMemberMapper, userStatService,
                badgeMapper, userBadgeMapper, wishMapper, redisTemplate,
                new com.cloudmart.wish.policy.WishAccessPolicy(), executor);

        when(operationMapper.insert(any(WishOperation.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        when(assetMapper.selectById(ASSET)).thenReturn(asset(80, "UNLIMITED", null));
        when(userAssetMapper.selectCount(any())).thenReturn(0L);
        when(userAssetMapper.insert(any(UserAsset.class))).thenReturn(1);
    }

    private VirtualAsset asset(int price, String stockMode, Integer stockRemaining) {
        VirtualAsset asset = new VirtualAsset();
        asset.setId(ASSET);
        asset.setAssetType(AssetType.SKIN);
        asset.setName("测试皮肤");
        asset.setPriceStarlight(price);
        asset.setPayMethod(AssetPayMethod.STARLIGHT);
        asset.setIsActive(true);
        asset.setStockMode(stockMode);
        asset.setStockRemaining(stockRemaining);
        return asset;
    }

    private ExchangeResultVO exchange(String key) {
        return collectionService.exchange(USER, ASSET, "STARLIGHT", key);
    }

    @Test
    @DisplayName("余额 100 买 80：成功且余额 20（修复 credited<cost 误判）")
    void balanceCoversCost_succeeds() {
        when(userStatService.spendStarlight(eq(USER), eq(80), any(), any())).thenReturn(20);

        ExchangeResultVO result = exchange("k1");

        assertThat(result.balanceAfter()).isEqualTo(20);
        assertThat(result.spentAmount()).isEqualTo(80);
        assertThat(result.assetId()).isEqualTo(ASSET);
    }

    @Test
    @DisplayName("余额 80 买 80：成功且余额 0")
    void exactBalance_succeeds() {
        when(userStatService.spendStarlight(eq(USER), eq(80), any(), any())).thenReturn(0);

        ExchangeResultVO result = exchange("k2");

        assertThat(result.balanceAfter()).isZero();
    }

    @Test
    @DisplayName("余额 79 买 80：钱包 402，不落归属")
    void insufficientBalance_throws402() {
        when(userStatService.spendStarlight(eq(USER), eq(80), any(), any()))
                .thenThrow(new BusinessException(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT, "星光余额不足"));

        assertThatThrownBy(() -> exchange("k3"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT));
        verify(userAssetMapper, never()).insert(any(UserAsset.class));
    }

    @Test
    @DisplayName("LIMITED 库存条件扣减：affected=0 → 售罄且不扣款（先库存后钱包）")
    void limitedStockSoldOut_noWalletCharge() {
        when(assetMapper.selectById(ASSET)).thenReturn(asset(80, "LIMITED", 0));
        when(assetMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> exchange("k4"))
                .isInstanceOf(BusinessException.class);
        verify(userStatService, never()).spendStarlight(any(), eq(80), any(), any());
    }

    @Test
    @DisplayName("LIMITED 库存充足：条件更新成功后继续扣款")
    void limitedStockAvailable_proceeds() {
        when(assetMapper.selectById(ASSET)).thenReturn(asset(80, "LIMITED", 3));
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(userStatService.spendStarlight(eq(USER), eq(80), any(), any())).thenReturn(20);

        ExchangeResultVO result = exchange("k5");

        assertThat(result.balanceAfter()).isEqualTo(20);
    }

    @Test
    @DisplayName("RMB 支付方式拒绝；非法支付方式 422")
    void paymentWhitelist() {
        assertThatThrownBy(() -> collectionService.exchange(USER, ASSET, "RMB", "k6"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> collectionService.exchange(USER, ASSET, "GOLD", "k7"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
    }

    @Test
    @DisplayName("未到开售时间拒绝")
    void validFromFuture_rejected() {
        VirtualAsset asset = asset(80, "UNLIMITED", null);
        asset.setValidFrom(LocalDateTime.now().plusDays(1));
        when(assetMapper.selectById(ASSET)).thenReturn(asset);

        assertThatThrownBy(() -> exchange("k8"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("已拥有资产拒绝（uk 幂等）")
    void alreadyOwned_rejected() {
        when(userAssetMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> exchange("k9"))
                .isInstanceOf(BusinessException.class);
        verify(userStatService, never()).spendStarlight(any(), eq(80), any(), any());
    }

    @Test
    @DisplayName("同键重试：重放第一次结果，不二次扣款、不二次扣库存（T08）")
    void sameKeyRetry_replaysWithoutDoubleCharge() {
        // LIMITED 资产：验证库存条件扣减也只发生一次
        when(assetMapper.selectById(ASSET)).thenReturn(asset(80, "LIMITED", 10));
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(userStatService.spendStarlight(eq(USER), eq(80), any(), any())).thenReturn(20);

        ExchangeResultVO first = exchange("k10");
        assertThat(first.balanceAfter()).isEqualTo(20);

        // 提取第一次提交的操作行，模拟第二次同键请求命中唯一键
        ArgumentCaptor<WishOperation> committed = ArgumentCaptor.forClass(WishOperation.class);
        verify(operationMapper).updateById(committed.capture());
        WishOperation row = committed.getValue();
        row.setStatus("COMPLETED");

        when(operationMapper.insert(any(WishOperation.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(operationMapper.selectOne(any())).thenReturn(row);

        ExchangeResultVO replayed = exchange("k10");

        assertThat(replayed.balanceAfter()).isEqualTo(20);
        // 扣款只发生一次
        verify(userStatService, times(1)).spendStarlight(any(), eq(80), any(), any());
        // 库存条件扣减只发生一次
        verify(assetMapper, times(1)).update(any(), any());
    }
}

package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetFriend;
import com.cloudmart.pet.enums.PetFriendStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetFriendMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetUserBlockService;
import com.cloudmart.pet.feign.UserFeignClient;
import com.cloudmart.pet.service.PetUserGuardService;
import com.cloudmart.pet.vo.PetFriendVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R21 好友接受并发与屏蔽复验测试：双方守卫按 userId 数值升序加锁（§5.2 统一锁序），
 * 锁内复验屏蔽关系——"申请后拉黑再接受"被拒绝；无屏蔽时正常建立双向好友。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetFriendServiceImpl 接受复验测试（R21）")
class PetFriendServiceImplTest {

    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;

    @Mock
    private PetService petService;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetUserBlockService userBlockService;
    @Mock
    private PetFriendMapper friendMapper;
    @Mock
    private PetUserGuardService guardService;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private WishFeignClient wishFeignClient;

    private PetFriendServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetFriend.class);
        TableInfoHelper.initTableInfo(assistant, Pet.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties properties = new PetProperties();
        service = new PetFriendServiceImpl(petService, petMapper, friendMapper,
                org.mockito.Mockito.mock(PetHomeService.class),
                org.mockito.Mockito.mock(PetRelationService.class),
                org.mockito.Mockito.mock(PetDailyQuestService.class),
                org.mockito.Mockito.mock(PetAchievementService.class),
                wishFeignClient, userFeignClient, eventProducer, properties,
                org.mockito.Mockito.mock(StringRedisTemplate.class),
                userBlockService, guardService);
        lenient().when(petService.requireOwnedPet(USER_A)).thenReturn(pet());
        lenient().when(friendMapper.selectCount(any())).thenReturn(0L);
        lenient().when(friendMapper.update(any(), any())).thenReturn(1);
        lenient().when(userFeignClient.batchGetUsers(anyList()))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(List.of()));
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(USER_A);
        pet.setName("小橘");
        pet.setIsPublic(true);
        return pet;
    }

    private PetFriend pendingRow(long owner, long friend) {
        PetFriend row = new PetFriend();
        row.setId(owner * 10 + friend);
        row.setUserId(owner);
        row.setFriendUserId(friend);
        row.setStatus(PetFriendStatus.PENDING.name());
        when(friendMapper.selectOne(any())).thenReturn(row);
        return row;
    }

    @Test
    @DisplayName("R21：双方守卫按 userId 数值升序加锁（交叉邀请不死锁）")
    void acceptLocksGuardsInNumericOrder() {
        pendingRow(USER_B, USER_A);
        when(userBlockService.isBlockedEitherWay(any(), any())).thenReturn(false);

        service.accept(USER_A, USER_B);

        InOrder inOrder = inOrder(guardService);
        // 100 < 200：先锁 100 再锁 200——同一对用户加锁顺序恒定
        inOrder.verify(guardService).lockGuard(USER_A);
        inOrder.verify(guardService).lockGuard(USER_B);
    }

    @Test
    @DisplayName("R21：申请后拉黑再接受 → 拒绝（锁内复验，原实现仅申请时校验一次）")
    void acceptBlockedAfterRequestRejected() {
        pendingRow(USER_B, USER_A);
        when(userBlockService.isBlockedEitherWay(USER_A, USER_B)).thenReturn(true);

        assertThatThrownBy(() -> service.accept(USER_A, USER_B))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_BLOCKED);
        verify(friendMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("R21：无屏蔽 → 双向好友建立成功")
    void acceptEstablishesBothDirections() {
        pendingRow(USER_B, USER_A);
        when(userBlockService.isBlockedEitherWay(any(), any())).thenReturn(false);
        PetFriend incoming = pendingRow(USER_B, USER_A);
        // 反向行已存在（接受时置 ACTIVE）
        PetFriend reverse = new PetFriend();
        reverse.setId(9L);
        reverse.setUserId(USER_A);
        reverse.setFriendUserId(USER_B);
        reverse.setStatus(PetFriendStatus.ACTIVE.name());
        when(friendMapper.selectOne(any())).thenReturn(incoming, reverse);

        PetFriendVO vo = service.accept(USER_A, USER_B);

        assertThat(vo).isNotNull();
        verify(friendMapper, org.mockito.Mockito.atLeastOnce()).update(any(), any());
    }
}

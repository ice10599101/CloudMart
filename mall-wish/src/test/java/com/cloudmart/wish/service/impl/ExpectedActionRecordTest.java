package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.dto.ExpectedActionRecordRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishExpectedAtAction;
import com.cloudmart.wish.enums.ExpectedActionType;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishAiGoalMapper;
import com.cloudmart.wish.repository.WishExpectedAtActionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.service.AssistantAiClient;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.UserStatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import com.cloudmart.wish.config.WishCryptoProperties;
import com.cloudmart.wish.util.ContentCipher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiAssistantServiceImpl 预期管理埋点单元测试")
class ExpectedActionRecordTest {

    @Mock
    private WishAiGoalMapper goalMapper;
    @Mock
    private WishAiConversationMapper conversationMapper;
    @Mock
    private WishExpectedAtActionMapper expectedAtActionMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private ConsentService consentService;
    @Mock
    private UserStatService userStatService;
    @Mock
    private AiRateLimiter aiRateLimiter;
    @Mock
    private AssistantAiClient assistantAiClient;
    @Mock
    private AiPromptService aiPromptService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AiAssistantServiceImpl aiAssistantService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long WISH_ID = 3001L;

    @BeforeEach
    void setUp() {
        aiAssistantService = new AiAssistantServiceImpl(goalMapper, conversationMapper,
                expectedAtActionMapper, new ContentCipher(new WishCryptoProperties()), wishMapper, consentService, userStatService,
                aiRateLimiter, new AiPrivacySanitizer(), assistantAiClient,
                aiPromptService, new WishAiProperties(), transactionTemplate);
    }

    private Wish buildWish(Long userId) {
        Wish wish = new Wish();
        wish.setId(WISH_ID);
        wish.setUserId(userId);
        return wish;
    }

    @Test
    @DisplayName("本人心愿 → 埋点落库（用户/心愿/选项完整）")
    void recordActionForOwnWish() {
        when(wishMapper.selectById(WISH_ID)).thenReturn(buildWish(USER_ID));

        aiAssistantService.recordExpectedAction(USER_ID,
                new ExpectedActionRecordRequest(WISH_ID, ExpectedActionType.TO_CAPSULE));

        ArgumentCaptor<WishExpectedAtAction> captor =
                ArgumentCaptor.forClass(WishExpectedAtAction.class);
        verify(expectedAtActionMapper).insert(captor.capture());
        WishExpectedAtAction inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(USER_ID);
        assertThat(inserted.getWishId()).isEqualTo(WISH_ID);
        assertThat(inserted.getAction()).isEqualTo(ExpectedActionType.TO_CAPSULE);
    }

    @Test
    @DisplayName("非本人心愿 → 404 WISH_NOT_FOUND（防存在性探测）")
    void rejectOthersWish() {
        when(wishMapper.selectById(WISH_ID)).thenReturn(buildWish(OTHER_USER_ID));

        assertThatThrownBy(() -> aiAssistantService.recordExpectedAction(USER_ID,
                new ExpectedActionRecordRequest(WISH_ID, ExpectedActionType.EXTEND)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_NOT_FOUND);
        verify(expectedAtActionMapper, org.mockito.Mockito.never()).insert(any(WishExpectedAtAction.class));
    }

    @Test
    @DisplayName("心愿不存在 → 404 WISH_NOT_FOUND")
    void rejectMissingWish() {
        when(wishMapper.selectById(WISH_ID)).thenReturn(null);

        assertThatThrownBy(() -> aiAssistantService.recordExpectedAction(USER_ID,
                new ExpectedActionRecordRequest(WISH_ID, ExpectedActionType.ADJUST)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_NOT_FOUND);
    }
}

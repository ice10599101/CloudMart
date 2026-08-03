package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.Tag;
import com.cloudmart.community.entity.TagSubscription;
import com.cloudmart.community.repository.TagMapper;
import com.cloudmart.community.repository.TagSubscriptionMapper;
import com.cloudmart.community.vo.TagVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagSubscriptionServiceImplTest {

    @Mock
    private TagSubscriptionMapper tagSubscriptionMapper;

    @Mock
    private TagMapper tagMapper;

    private TagSubscriptionServiceImpl tagSubscriptionService;

    private static final Long USER_ID = 1L;
    private static final Long TAG_ID = 10L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TagSubscription.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Tag.class);
    }

    @BeforeEach
    void setUp() {
        tagSubscriptionService = new TagSubscriptionServiceImpl(tagSubscriptionMapper, tagMapper);
    }

    private Tag buildTag() {
        Tag tag = new Tag();
        tag.setId(TAG_ID);
        tag.setName("tech");
        tag.setIcon("icon.png");
        tag.setPostCount(42);
        tag.setIsHot(true);
        tag.setStatus(1);
        tag.setCreatedAt(LocalDateTime.now());
        return tag;
    }

    private TagSubscription buildTagSubscription() {
        TagSubscription sub = new TagSubscription();
        sub.setId(1L);
        sub.setUserId(USER_ID);
        sub.setTagId(TAG_ID);
        sub.setCreatedAt(LocalDateTime.now());
        return sub;
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should throw when tag does not exist")
        void subscribe_tagNotFound_throwsException() {
            when(tagMapper.selectById(TAG_ID)).thenReturn(null);

            assertThatThrownBy(() -> tagSubscriptionService.subscribe(USER_ID, TAG_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("TAG_NOT_FOUND");
                    });

            verify(tagSubscriptionMapper, never()).insert(any(TagSubscription.class));
        }

        @Test
        @DisplayName("should not insert when already subscribed")
        void subscribe_alreadySubscribed_noInsert() {
            when(tagMapper.selectById(TAG_ID)).thenReturn(buildTag());
            when(tagSubscriptionMapper.selectCount(any())).thenReturn(1L);

            tagSubscriptionService.subscribe(USER_ID, TAG_ID);

            verify(tagSubscriptionMapper, never()).insert(any(TagSubscription.class));
        }

        @Test
        @DisplayName("should insert subscription for new subscribe")
        void subscribe_newSubscription_inserts() {
            when(tagMapper.selectById(TAG_ID)).thenReturn(buildTag());
            when(tagSubscriptionMapper.selectCount(any())).thenReturn(0L);

            tagSubscriptionService.subscribe(USER_ID, TAG_ID);

            verify(tagSubscriptionMapper).insert(any(TagSubscription.class));
        }
    }

    @Nested
    @DisplayName("unsubscribe")
    class UnsubscribeTests {

        @Test
        @DisplayName("should delete subscription by wrapper")
        void unsubscribe_deletesSubscription() {
            when(tagSubscriptionMapper.delete(any())).thenReturn(1);

            tagSubscriptionService.unsubscribe(USER_ID, TAG_ID);

            verify(tagSubscriptionMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("isSubscribed")
    class IsSubscribedTests {

        @Test
        @DisplayName("should return false when userId is null")
        void isSubscribed_nullUserId_returnsFalse() {
            boolean result = tagSubscriptionService.isSubscribed(null, TAG_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when subscription exists")
        void isSubscribed_exists_returnsTrue() {
            when(tagSubscriptionMapper.selectCount(any())).thenReturn(1L);

            boolean result = tagSubscriptionService.isSubscribed(USER_ID, TAG_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when subscription does not exist")
        void isSubscribed_notExists_returnsFalse() {
            when(tagSubscriptionMapper.selectCount(any())).thenReturn(0L);

            boolean result = tagSubscriptionService.isSubscribed(USER_ID, TAG_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getSubscribedTags")
    class GetSubscribedTagsTests {

        @Test
        @DisplayName("should return empty list when no subscriptions")
        void getSubscribedTags_noSubscriptions_returnsEmpty() {
            when(tagSubscriptionMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<TagVO> result = tagSubscriptionService.getSubscribedTags(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return mapped TagVO list when subscriptions exist")
        void getSubscribedTags_hasSubscriptions_returnsTagVOs() {
            TagSubscription sub = buildTagSubscription();
            Tag tag = buildTag();

            when(tagSubscriptionMapper.selectList(any())).thenReturn(List.of(sub));
            when(tagMapper.selectBatchIds(List.of(TAG_ID))).thenReturn(List.of(tag));

            List<TagVO> result = tagSubscriptionService.getSubscribedTags(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(TAG_ID);
            assertThat(result.get(0).name()).isEqualTo("tech");
            assertThat(result.get(0).postCount()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("getSubscriberUserIds")
    class GetSubscriberUserIdsTests {

        @Test
        @DisplayName("should return list of subscriber user IDs")
        void getSubscriberUserIds_returnsUserIds() {
            TagSubscription sub1 = new TagSubscription();
            sub1.setUserId(1L);
            sub1.setTagId(TAG_ID);
            TagSubscription sub2 = new TagSubscription();
            sub2.setUserId(2L);
            sub2.setTagId(TAG_ID);

            when(tagSubscriptionMapper.selectList(any())).thenReturn(List.of(sub1, sub2));

            List<Long> result = tagSubscriptionService.getSubscriberUserIds(TAG_ID);

            assertThat(result).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("should return empty list when no subscribers")
        void getSubscriberUserIds_noSubscribers_returnsEmpty() {
            when(tagSubscriptionMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<Long> result = tagSubscriptionService.getSubscriberUserIds(TAG_ID);

            assertThat(result).isEmpty();
        }
    }
}

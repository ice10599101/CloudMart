package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.dto.CreatePollRequest;
import com.cloudmart.community.entity.CommunityPoll;
import com.cloudmart.community.entity.CommunityPollOption;
import com.cloudmart.community.entity.CommunityPollVote;
import com.cloudmart.community.repository.CommunityPollMapper;
import com.cloudmart.community.repository.CommunityPollOptionMapper;
import com.cloudmart.community.repository.CommunityPollVoteMapper;
import com.cloudmart.community.vo.PollVO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PollServiceImpl 投票服务单元测试")
@ExtendWith(MockitoExtension.class)
class PollServiceImplTest {

    private static final Long CREATOR_ID = 10001L;
    private static final Long VOTER_ID = 20002L;

    @Mock
    private CommunityPollMapper pollMapper;
    @Mock
    private CommunityPollOptionMapper optionMapper;
    @Mock
    private CommunityPollVoteMapper voteMapper;

    private PollServiceImpl pollService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.cloudmart.community.repository.CommunityPollMapper");
        TableInfoHelper.initTableInfo(assistant, CommunityPoll.class);
        MapperBuilderAssistant optionAssistant = new MapperBuilderAssistant(configuration, "");
        optionAssistant.setCurrentNamespace("com.cloudmart.community.repository.CommunityPollOptionMapper");
        TableInfoHelper.initTableInfo(optionAssistant, CommunityPollOption.class);
        MapperBuilderAssistant voteAssistant = new MapperBuilderAssistant(configuration, "");
        voteAssistant.setCurrentNamespace("com.cloudmart.community.repository.CommunityPollVoteMapper");
        TableInfoHelper.initTableInfo(voteAssistant, CommunityPollVote.class);
    }

    @BeforeEach
    void setUp() {
        pollService = new PollServiceImpl(pollMapper, optionMapper, voteMapper);
    }

    private CreatePollRequest createRequest(String id) {
        return new CreatePollRequest(id, "POST", "9001", "周末去哪？", false, List.of("爬山", "看电影"));
    }

    private CommunityPollOption option(long id, String content, int sort) {
        CommunityPollOption entity = new CommunityPollOption();
        entity.setId(id);
        entity.setPollId("poll-1");
        entity.setContent(content);
        entity.setSort(sort);
        return entity;
    }

    private CommunityPollVote vote(long optionId, long userId) {
        CommunityPollVote entity = new CommunityPollVote();
        entity.setPollId("poll-1");
        entity.setOptionId(optionId);
        entity.setUserId(userId);
        return entity;
    }

    @Nested
    @DisplayName("createPoll 创建投票")
    class CreateTests {

        @Test
        @DisplayName("首次创建 - 主表与选项按 sort 落库")
        void shouldCreatePollWithOptions() {
            CommunityPoll persisted = new CommunityPoll();
            persisted.setId("poll-1");
            persisted.setCreatorId(CREATOR_ID);
            persisted.setQuestion("周末去哪？");
            persisted.setIsMultiple(false);
            when(pollMapper.selectById("poll-1")).thenReturn(null).thenReturn(persisted);
            when(optionMapper.selectList(any())).thenReturn(List.of(
                    option(1L, "爬山", 0), option(2L, "看电影", 1)));
            when(voteMapper.selectList(any())).thenReturn(List.of());

            PollVO vo = pollService.createPoll(CREATOR_ID, createRequest("poll-1"));

            assertThat(vo.question()).isEqualTo("周末去哪？");
            assertThat(vo.options()).hasSize(2);
            assertThat(vo.options().get(0).content()).isEqualTo("爬山");

            ArgumentCaptor<CommunityPollOption> optionCaptor = ArgumentCaptor.forClass(CommunityPollOption.class);
            verify(optionMapper, times(2)).insert(optionCaptor.capture());
            assertThat(optionCaptor.getAllValues()).extracting(CommunityPollOption::getSort)
                    .containsExactly(0, 1);
        }

        @Test
        @DisplayName("同 ID 同创建者重复提交 - 幂等返回既有投票不重复插入")
        void shouldBeIdempotentForSameCreator() {
            CommunityPoll existing = new CommunityPoll();
            existing.setId("poll-1");
            existing.setCreatorId(CREATOR_ID);
            existing.setQuestion("周末去哪？");
            existing.setIsMultiple(false);
            when(pollMapper.selectById("poll-1")).thenReturn(existing);
            when(optionMapper.selectList(any())).thenReturn(List.of());
            when(voteMapper.selectList(any())).thenReturn(List.of());

            pollService.createPoll(CREATOR_ID, createRequest("poll-1"));

            verify(pollMapper, never()).insert(any(CommunityPoll.class));
            verify(optionMapper, never()).insert(any(CommunityPollOption.class));
        }

        @Test
        @DisplayName("同 ID 不同创建者 - 403 拒绝")
        void shouldRejectDifferentCreatorWithSameId() {
            CommunityPoll existing = new CommunityPoll();
            existing.setId("poll-1");
            existing.setCreatorId(999L);
            when(pollMapper.selectById("poll-1")).thenReturn(existing);

            assertThatThrownBy(() -> pollService.createPoll(CREATOR_ID, createRequest("poll-1")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("有效选项不足 2 个 - 拒绝创建")
        void shouldRejectWhenTooFewValidOptions() {
            when(pollMapper.selectById("poll-1")).thenReturn(null);

            assertThatThrownBy(() -> pollService.createPoll(CREATOR_ID,
                    new CreatePollRequest("poll-1", "POST", "9001", "q", false, List.of("  ", ""))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("POLL_OPTION_INVALID");
        }
    }

    @Nested
    @DisplayName("vote 投票")
    class VoteTests {

        @Test
        @DisplayName("投票成功 - 按选项插入投票记录")
        void shouldInsertVotes() {
            CommunityPoll poll = new CommunityPoll();
            poll.setId("poll-1");
            poll.setIsMultiple(true);
            when(pollMapper.selectById("poll-1")).thenReturn(poll);
            when(optionMapper.selectList(any())).thenReturn(List.of(
                    option(1L, "爬山", 0), option(2L, "看电影", 1)));
            when(voteMapper.selectCount(any())).thenReturn(0L);

            pollService.vote("poll-1", VOTER_ID, List.of(1L, 2L));

            ArgumentCaptor<CommunityPollVote> voteCaptor = ArgumentCaptor.forClass(CommunityPollVote.class);
            verify(voteMapper, times(2)).insert(voteCaptor.capture());
            assertThat(voteCaptor.getAllValues()).extracting(CommunityPollVote::getOptionId)
                    .containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("单选投 2 项 - 拒绝")
        void shouldRejectMultipleChoicesOnSinglePoll() {
            CommunityPoll poll = new CommunityPoll();
            poll.setId("poll-1");
            poll.setIsMultiple(false);
            when(pollMapper.selectById("poll-1")).thenReturn(poll);
            when(optionMapper.selectList(any())).thenReturn(List.of(
                    option(1L, "爬山", 0), option(2L, "看电影", 1)));

            assertThatThrownBy(() -> pollService.vote("poll-1", VOTER_ID, List.of(1L, 2L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("POLL_OPTION_INVALID");
        }

        @Test
        @DisplayName("选项不属于该投票 - 拒绝")
        void shouldRejectForeignOption() {
            CommunityPoll poll = new CommunityPoll();
            poll.setId("poll-1");
            poll.setIsMultiple(true);
            when(pollMapper.selectById("poll-1")).thenReturn(poll);
            when(optionMapper.selectList(any())).thenReturn(List.of(option(1L, "爬山", 0)));

            assertThatThrownBy(() -> pollService.vote("poll-1", VOTER_ID, List.of(777L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("POLL_OPTION_INVALID");
        }

        @Test
        @DisplayName("重复投票 - 409 拒绝")
        void shouldRejectDuplicateVote() {
            CommunityPoll poll = new CommunityPoll();
            poll.setId("poll-1");
            poll.setIsMultiple(true);
            when(pollMapper.selectById("poll-1")).thenReturn(poll);
            when(optionMapper.selectList(any())).thenReturn(List.of(option(1L, "爬山", 0)));
            when(voteMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> pollService.vote("poll-1", VOTER_ID, List.of(1L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("POLL_ALREADY_VOTED");

            verify(voteMapper, never()).insert(any(CommunityPollVote.class));
        }
    }

    @Test
    @DisplayName("getPoll 聚合 - 票数与我的选择")
    void shouldAggregateVotesAndMyOptions() {
        CommunityPoll poll = new CommunityPoll();
        poll.setId("poll-1");
        poll.setQuestion("q");
        poll.setIsMultiple(true);
        when(pollMapper.selectById("poll-1")).thenReturn(poll);
        when(optionMapper.selectList(any())).thenReturn(List.of(
                option(1L, "爬山", 0), option(2L, "看电影", 1)));
        when(voteMapper.selectList(any())).thenReturn(List.of(
                vote(1L, VOTER_ID), vote(1L, 30003L), vote(2L, 30003L)));

        PollVO vo = pollService.getPoll("poll-1", VOTER_ID);

        assertThat(vo.totalVotes()).isEqualTo(2);
        assertThat(vo.options().get(0).voteCount()).isEqualTo(2);
        assertThat(vo.options().get(1).voteCount()).isEqualTo(1);
        assertThat(vo.myOptionIds()).containsExactly(1L);
    }
}

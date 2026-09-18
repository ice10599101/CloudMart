package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.dto.CreatePollRequest;
import com.cloudmart.community.entity.CommunityPoll;
import com.cloudmart.community.entity.CommunityPollOption;
import com.cloudmart.community.entity.CommunityPollVote;
import com.cloudmart.community.repository.CommunityPollMapper;
import com.cloudmart.community.repository.CommunityPollOptionMapper;
import com.cloudmart.community.repository.CommunityPollVoteMapper;
import com.cloudmart.community.service.PollService;
import com.cloudmart.community.vo.PollVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 投票服务实现（编辑器附件，V10）。
 *
 * <p>关键设计：客户端 UUID 为主键——发布链路「先 POST /polls 幂等落库，再保存宿主正文」，
 * 无需发布后回写正文；选项/票数经一次聚合查询组装，避免 N+1。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PollServiceImpl implements PollService {

    private final CommunityPollMapper pollMapper;
    private final CommunityPollOptionMapper optionMapper;
    private final CommunityPollVoteMapper voteMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PollVO createPoll(Long userId, CreatePollRequest request) {
        CommunityPoll existing = pollMapper.selectById(request.id());
        if (existing != null) {
            if (!existing.getCreatorId().equals(userId)) {
                throw new BusinessException("FORBIDDEN", "该投票 ID 已被使用");
            }
            // 幂等：发布重试/草稿反复保存时直接返回既有投票
            return getPoll(existing.getId(), userId);
        }

        List<String> options = request.options().stream().map(String::trim).filter(option -> !option.isEmpty()).toList();
        if (options.size() < 2) {
            throw new BusinessException("POLL_OPTION_INVALID", "至少需要 2 个有效选项");
        }

        CommunityPoll poll = new CommunityPoll();
        poll.setId(request.id());
        poll.setTargetType(request.targetType());
        poll.setTargetId(request.targetId());
        poll.setCreatorId(userId);
        poll.setQuestion(request.question().trim());
        poll.setIsMultiple(Boolean.TRUE.equals(request.multiple()));
        pollMapper.insert(poll);

        for (int index = 0; index < options.size(); index++) {
            CommunityPollOption option = new CommunityPollOption();
            option.setPollId(poll.getId());
            option.setContent(options.get(index));
            option.setSort(index);
            optionMapper.insert(option);
        }
        log.info("投票已创建: pollId={}, userId={}, targetType={}, targetId={}",
                poll.getId(), userId, poll.getTargetType(), poll.getTargetId());
        return getPoll(poll.getId(), userId);
    }

    @Override
    public PollVO getPoll(String pollId, Long userId) {
        CommunityPoll poll = pollMapper.selectById(pollId);
        if (poll == null) {
            throw new BusinessException("POLL_NOT_FOUND", "投票不存在");
        }
        List<CommunityPollOption> options = optionMapper.selectList(new LambdaQueryWrapper<CommunityPollOption>()
                .eq(CommunityPollOption::getPollId, pollId)
                .orderByAsc(CommunityPollOption::getSort)
                .orderByAsc(CommunityPollOption::getId));

        List<CommunityPollVote> votes = voteMapper.selectList(new LambdaQueryWrapper<CommunityPollVote>()
                .eq(CommunityPollVote::getPollId, pollId));
        Map<Long, Long> voteCountByOption = votes.stream()
                .collect(Collectors.groupingBy(CommunityPollVote::getOptionId, Collectors.counting()));
        long distinctVoters = votes.stream().map(CommunityPollVote::getUserId).distinct().count();

        Set<Long> myOptionIds = userId == null ? Set.of() : votes.stream()
                .filter(vote -> vote.getUserId().equals(userId))
                .map(CommunityPollVote::getOptionId)
                .collect(Collectors.toSet());

        List<PollVO.PollOptionVO> optionVOs = options.stream()
                .map(option -> new PollVO.PollOptionVO(
                        option.getId(), option.getContent(), voteCountByOption.getOrDefault(option.getId(), 0L)))
                .toList();

        return new PollVO(poll.getId(), poll.getQuestion(), poll.getIsMultiple(), optionVOs,
                distinctVoters, List.copyOf(myOptionIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vote(String pollId, Long userId, List<Long> optionIds) {
        CommunityPoll poll = pollMapper.selectById(pollId);
        if (poll == null) {
            throw new BusinessException("POLL_NOT_FOUND", "投票不存在");
        }
        List<Long> distinctOptionIds = List.copyOf(new HashSet<>(optionIds));
        Set<Long> pollOptionIds = optionMapper.selectList(new LambdaQueryWrapper<CommunityPollOption>()
                        .eq(CommunityPollOption::getPollId, pollId))
                .stream().map(CommunityPollOption::getId).collect(Collectors.toSet());
        if (!pollOptionIds.containsAll(distinctOptionIds)) {
            throw new BusinessException("POLL_OPTION_INVALID", "存在不属于该投票的选项");
        }
        if (!Boolean.TRUE.equals(poll.getIsMultiple()) && distinctOptionIds.size() != 1) {
            throw new BusinessException("POLL_OPTION_INVALID", "单选投票只能选择 1 个选项");
        }

        boolean alreadyVoted = voteMapper.selectCount(new LambdaQueryWrapper<CommunityPollVote>()
                .eq(CommunityPollVote::getPollId, pollId)
                .eq(CommunityPollVote::getUserId, userId)) > 0;
        if (alreadyVoted) {
            throw new BusinessException("POLL_ALREADY_VOTED", "已参与过该投票");
        }

        for (Long optionId : distinctOptionIds) {
            CommunityPollVote vote = new CommunityPollVote();
            vote.setPollId(pollId);
            vote.setOptionId(optionId);
            vote.setUserId(userId);
            voteMapper.insert(vote);
        }
        log.info("投票成功: pollId={}, userId={}, optionIds={}", pollId, userId, distinctOptionIds);
    }
}

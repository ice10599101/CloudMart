package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.CommunityPollVote;
import org.apache.ibatis.annotations.Mapper;

/** 投票记录 Mapper（V10/C03，编辑器附件）。 */
@Mapper
public interface CommunityPollVoteMapper extends BaseMapper<CommunityPollVote> {

    /** C03/QA25：选票登记——uk(poll_id,user_id) 判重（单选并发不同选项只有一个赢家） */
    @org.apache.ibatis.annotations.Insert("INSERT IGNORE INTO community_poll_ballots "
            + "(poll_id, user_id) VALUES (#{pollId}, #{userId})")
    int insertBallot(@org.apache.ibatis.annotations.Param("pollId") String pollId,
                     @org.apache.ibatis.annotations.Param("userId") Long userId);
}

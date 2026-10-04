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

    /** T21：按选项聚合票数（GROUP BY，不随总票数线性增长） */
    @org.apache.ibatis.annotations.Select("SELECT option_id AS optionId, COUNT(*) AS cnt "
            + "FROM community_poll_votes WHERE poll_id = #{pollId} GROUP BY option_id")
    java.util.List<java.util.Map<String, Object>> countByOption(
            @org.apache.ibatis.annotations.Param("pollId") String pollId);

    /** T21：去重参与人数 */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(DISTINCT user_id) "
            + "FROM community_poll_votes WHERE poll_id = #{pollId}")
    long countDistinctVoters(@org.apache.ibatis.annotations.Param("pollId") String pollId);

    /** T21：本人选项（仅本人行，不做全表读取） */
    @org.apache.ibatis.annotations.Select("SELECT option_id FROM community_poll_votes "
            + "WHERE poll_id = #{pollId} AND user_id = #{userId}")
    java.util.List<Long> selectMyOptionIds(@org.apache.ibatis.annotations.Param("pollId") String pollId,
                                           @org.apache.ibatis.annotations.Param("userId") Long userId);
}

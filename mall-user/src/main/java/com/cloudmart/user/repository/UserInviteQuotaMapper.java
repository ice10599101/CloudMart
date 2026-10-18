package com.cloudmart.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.user.entity.UserInviteQuota;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 邀请额度（T15）：INSERT IGNORE 建行 + SELECT ... FOR UPDATE 行锁 + 条件自增。
 * 全部在调用方事务内执行，行锁串行化同一 (inviter, type, period) 的并发预占。
 */
public interface UserInviteQuotaMapper extends BaseMapper<UserInviteQuota> {

    @Insert("INSERT IGNORE INTO user_invite_quota (inviter_id, quota_type, period, used_count) "
            + "VALUES (#{inviterId}, #{quotaType}, #{period}, 0)")
    int insertIgnore(@Param("inviterId") Long inviterId,
                     @Param("quotaType") String quotaType,
                     @Param("period") String period);

    @Select("SELECT id, inviter_id, quota_type, period, used_count "
            + "FROM user_invite_quota WHERE inviter_id = #{inviterId} "
            + "AND quota_type = #{quotaType} AND period = #{period} FOR UPDATE")
    UserInviteQuota selectForUpdate(@Param("inviterId") Long inviterId,
                                    @Param("quotaType") String quotaType,
                                    @Param("period") String period);

    @Update("UPDATE user_invite_quota SET used_count = used_count + 1, updated_at = NOW(3) "
            + "WHERE inviter_id = #{inviterId} AND quota_type = #{quotaType} AND period = #{period}")
    int incrementUsed(@Param("inviterId") Long inviterId,
                      @Param("quotaType") String quotaType,
                      @Param("period") String period);
}

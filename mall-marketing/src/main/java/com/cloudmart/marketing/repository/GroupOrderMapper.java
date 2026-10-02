package com.cloudmart.marketing.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.marketing.entity.GroupOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 拼团组 Mapper（T10）：人数与状态全部 CAS 条件更新——最后一人加入、
 * 重复成团消息、超时扫描并发时只有一个终态生效，重复投递无害。
 */
@Mapper
public interface GroupOrderMapper extends BaseMapper<GroupOrder> {

    /** 开团：活动开团数原子递增（0 行 = 已达最大开团数） */
    @Update("UPDATE group_activities SET current_groups = current_groups + 1 "
            + "WHERE id = #{activityId} AND (max_groups = 0 OR current_groups < max_groups)")
    int incrementActivityGroups(@Param("activityId") Long activityId);

    /** 参团人数原子递增（0 行 = 组已满或已终态）——DB 权威计数，Redis 投影只做预筛 */
    @Update("UPDATE group_orders SET current_number = current_number + 1 "
            + "WHERE id = #{groupOrderId} AND status = 'PENDING' AND current_number < target_number")
    int incrementMemberCount(@Param("groupOrderId") Long groupOrderId);

    /** CAS PENDING → SUCCESS 成团（0 = 已终态/人数不足，重复成团消息无害） */
    @Update("UPDATE group_orders SET status = 'SUCCESS', success_time = #{successTime} "
            + "WHERE id = #{groupOrderId} AND status = 'PENDING' AND current_number >= target_number")
    int markSuccess(@Param("groupOrderId") Long groupOrderId, @Param("successTime") LocalDateTime successTime);

    /** CAS PENDING → EXPIRED 过期（0 = 已终态；与成团竞争只允许一方生效） */
    @Update("UPDATE group_orders SET status = 'EXPIRED' "
            + "WHERE id = #{groupOrderId} AND status = 'PENDING'")
    int markExpired(@Param("groupOrderId") Long groupOrderId);

    /** 成员状态 CAS（重复消息幂等）：失败团释放预留权益（不标 REFUNDED——未收款无退款事实） */
    @Update("UPDATE group_members SET status = 'EXPIRED' "
            + "WHERE group_order_id = #{groupOrderId} AND status = 'JOINED'")
    int markMembersExpired(@Param("groupOrderId") Long groupOrderId);
}

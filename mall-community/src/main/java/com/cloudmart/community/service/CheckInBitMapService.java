package com.cloudmart.community.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 签到 BitMap 服务。
 * <p>
 * 基于 Redis BitMap 实现签到记录的存储与查询，每个用户每月对应一个 BitMap key，
 * bit offset = dayOfMonth - 1。31 bit 即可表示一整月的签到情况，极大节省内存。
 * <p>
 * Key 规范：{@code checkin:bitmap:{userId}:{yyyyMM}}，TTL 35 天 + 随机抖动。
 */
public interface CheckInBitMapService {

    /**
     * 设置指定日期的签到标记（SETBIT offset 1）。
     *
     * @param userId 用户ID
     * @param date   签到日期
     * @return true 表示此前已签到（重复签到），false 表示首次签到
     */
    boolean setBit(Long userId, LocalDate date);

    /**
     * 查询指定日期是否已签到（GETBIT）。
     *
     * @param userId 用户ID
     * @param date   查询日期
     * @return true 表示已签到
     */
    boolean getBit(Long userId, LocalDate date);

    /**
     * 获取指定月份从第1天到指定天数为止的签到记录（BITFIELD GET u{dayCount} 0）。
     * 返回的是一个 0/1 列表，索引 0 对应第1天，索引 n-1 对应第n天。
     *
     * @param userId   用户ID
     * @param year     年份
     * @param month    月份（1-12）
     * @param dayCount 需要获取的天数（通常为当天 dayOfMonth 或当月总天数）
     * @return 0/1 列表，长度等于 dayCount；如果 BitMap 不存在则返回全 0
     */
    List<Integer> getMonthBits(Long userId, int year, int month, int dayCount);

    /**
     * 计算从指定日期开始向前统计的连续签到天数。
     * <p>
     * 如果指定日期是本月第1天且已签到，会额外检查上月最后一天是否签到，
     * 若上月最后一天已签到则递归统计上月连续天数并累加。
     *
     * @param userId 用户ID
     * @param date   起始日期（从该天开始向前统计）
     * @return 连续签到天数，未签到返回 0
     */
    int countContinuousDays(Long userId, LocalDate date);
}

package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 签到日历（文档 L855：GET /wish/my/checkin/calendar?month=yyyy-MM）。
 *
 * @param signedDates      当月已签到日期（yyyy-MM-dd 升序）
 * @param consecutiveDays  截至当前连续签到天数（按最新签到日回溯）
 * @param totalDays        历史累计签到天数
 */
@Schema(description = "签到日历")
public record SigninCalendarVO(
        @Schema(description = "当月已签到日期（yyyy-MM-dd）") List<String> signedDates,
        @Schema(description = "截至当前连续签到天数") int consecutiveDays,
        @Schema(description = "历史累计签到天数") int totalDays) {
}

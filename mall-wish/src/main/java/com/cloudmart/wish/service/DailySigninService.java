package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.DailySigninVO;
import com.cloudmart.wish.vo.SigninCalendarVO;

/**
 * 用户每日签到服务（文档 2.6：POST /wish/my/checkin + GET /wish/my/checkin/calendar）。
 *
 * <p>与心愿打卡（{@code WishService#checkinWish}，心愿维度 +3）独立：
 * 用户维度每日一次，签到发放星光 +5（文档 6.1，流水 source=SIGNIN）。</p>
 *
 * <p>签到响应携带等级提升事件（文档 6.5 判定规则），供三端触发庆祝弹窗
 * 与 APP 本地推送（前端 L1912/L1917）。</p>
 */
public interface DailySigninService {

    /**
     * 每日签到。
     *
     * <p>幂等：{@code uk_signin_daily}（user_id + signin_date）唯一键兜底并发，
     * 重复签到抛 409 WISH_ALREADY_SIGNED_IN。签到 + 星光发放 + 等级提升
     * 检测同事务（文档 6.4：流水与余额更新必须同事务）。</p>
     *
     * @param userId 用户 ID
     * @return 签到结果（连续天数 + 本次入账星光 + 明日奖励 + 等级提升事件）
     */
    DailySigninVO signin(Long userId);

    /**
     * 签到日历。
     *
     * <p>只读：返回指定月份已签到日期 + 截至当前连续签到天数 + 历史累计签到天数。</p>
     *
     * @param userId 用户 ID
     * @param month  月份（yyyy-MM，需校验格式）
     * @return 签到日历
     */
    SigninCalendarVO getCalendar(Long userId, String month);
}

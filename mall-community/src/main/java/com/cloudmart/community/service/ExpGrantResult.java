package com.cloudmart.community.service;

/**
 * R21 经验发放结果：让调用方（签到/内部发奖端点/前端）知道真实到账，
 * 不再把理论奖励值当作已发放。
 *
 * @param requested     请求发放的经验值
 * @param credited      实际到账经验值（日上限截断后 = min(requested, 剩余额度)）
 * @param deduplicated  是否因 (source,bizId) 奖励事实已存在而幂等跳过（重复不重发）
 * @param cappedReason  未足额发放原因；null=足额或零额非因截断；当前仅 "DAILY_EXP_CAP"
 */
public record ExpGrantResult(int requested, int credited, boolean deduplicated, String cappedReason) {

    /** 日上限截断原因码（200/日，与 GrowthServiceImpl.DAILY_EXP_CAP 对齐） */
    public static final String REASON_DAILY_CAP = "DAILY_EXP_CAP";

    public static ExpGrantResult duplicate(int requested) {
        return new ExpGrantResult(requested, 0, true, null);
    }

    public static ExpGrantResult granted(int requested, int credited) {
        return new ExpGrantResult(requested, credited, false,
                credited < requested ? REASON_DAILY_CAP : null);
    }
}

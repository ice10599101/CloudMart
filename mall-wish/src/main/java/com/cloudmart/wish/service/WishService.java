package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.MyWishListQuery;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.dto.WishListQuery;
import com.cloudmart.wish.vo.MyWishListItemVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishDeleteResultVO;
import com.cloudmart.wish.vo.WishListItemVO;
import com.cloudmart.wish.vo.WishSparkVO;
import com.cloudmart.wish.vo.WishUpdateResultVO;
import com.cloudmart.wish.vo.WishVO;

import java.util.List;

/**
 * 心愿核心服务接口。
 *
 * <p>对应文档 2.1 节心愿核心 API：CRUD + cursor 分页 + 软删 + 越权校验。</p>
 */
public interface WishService {

    /**
     * 创建心愿。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>根据 visibility 自动设置 enableAiReply / auditStrategy / triggerEnvEmo</li>
     *   <li>TREE_HOLE 类型启用 AI 回复 + STRICT 审核 + 情绪环境联动</li>
     *   <li>初始状态：status=ACTIVE, fruitType=GLOW, auditStatus=PENDING</li>
     *   <li>同事务初始化 wish_progress 表 + wish_user_stat 计数</li>
     * </ul>
     *
     * @param userId  作者用户 ID
     * @param request 创建请求
     * @return 创建结果 VO
     */
    WishCreateResultVO createWish(Long userId, CreateWishRequest request);

    /**
     * 更新心愿（仅作者可操作）。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>非作者返回 403 WISH_NOT_AUTHOR</li>
     *   <li>心愿不存在返回 404 WISH_NOT_FOUND</li>
     *   <li>FULFILLED 状态尝试设置 SPARK 果实类型返回 409 WISH_STATUS_CONFLICT</li>
     * </ul>
     *
     * @param userId  当前用户 ID
     * @param wishId  心愿 ID
     * @param request 更新请求
     * @return 更新结果 VO
     */
    WishUpdateResultVO updateWish(Long userId, Long wishId, UpdateWishRequest request);

    /**
     * 软删心愿（仅作者可操作）。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>非作者返回 403 WISH_NOT_AUTHOR</li>
     *   <li>心愿不存在返回 404 WISH_NOT_FOUND</li>
     *   <li>同事务 activeWishes - 1（totalWishes 不变）</li>
     * </ul>
     *
     * @param userId 当前用户 ID
     * @param wishId 心愿 ID
     * @return 删除结果 VO
     */
    WishDeleteResultVO deleteWish(Long userId, Long wishId);

    /**
     * 设为星火永久收藏（仅作者可操作，文档 2.3 心愿果实系统）。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>fruit_type: BLOOM → SPARK；status 保持 FULFILLED 不变</li>
     *   <li>仅 FULFILLED（已还愿）状态可操作，否则 409 WISH_NOT_FULFILLED</li>
     *   <li>幂等：已是 SPARK 重复调用直接返回成功（永久收藏语义）</li>
     *   <li>非作者返回 403 WISH_NOT_AUTHOR；心愿不存在返回 404 WISH_NOT_FOUND</li>
     *   <li>SPARK 心愿在世界生命树永久展示，不受作者归档影响
     *       （WishStatus 枚举契约：SPARK 不可归档）</li>
     * </ul>
     *
     * @param userId 当前用户 ID
     * @param wishId 心愿 ID
     * @return 星火设置结果 VO
     */
    WishSparkVO sparkWish(Long userId, Long wishId);

    /**
     * 获取心愿详情。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>PRIVATE/TREE_HOLE 心愿仅作者可见，非作者返回 404 WISH_NOT_FOUND（不暴露存在性）</li>
     *   <li>审核未通过（REJECTED/AUTO_HIDDEN）且非作者返回 404</li>
     *   <li>详情包含最近 10 条成长记录 + 进度信息</li>
     *   <li>作者信息通过 Feign 调用 mall-user 填充</li>
     * </ul>
     *
     * @param wishId 心愿 ID
     * @param userId 当前用户 ID（可空，未登录时仅可查看 PUBLIC + APPROVED）
     * @return 心愿详情 VO
     */
    WishVO getWishDetail(Long wishId, Long userId);

    /**
     * 心愿列表（cursor 分页，用户端公开列表）。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>强制 visibility=PUBLIC + auditStatus=APPROVED + isVisible=true</li>
     *   <li>按 created_at DESC, id DESC 排序</li>
     *   <li>cursor 为上一页最后一条记录的 id（字符串形式）</li>
     *   <li>批量填充 categoryName + 作者信息（避免 N+1）</li>
     * </ul>
     *
     * @param query  查询参数
     * @return 心愿列表项 VO 列表（调用方根据 hasNext 构造 CursorMeta）
     */
    WishListPage listWishes(WishListQuery query);

    /**
     * 我的心愿列表（cursor 分页，仅作者自己的心愿）。
     *
     * @param userId 当前用户 ID
     * @param query  查询参数
     * @return 我的心愿列表项 VO 列表
     */
    MyWishListPage listMyWishes(Long userId, MyWishListQuery query);

    /**
     * 心愿列表分页结果（cursor 分页）。
     *
     * @param records    当前页记录
     * @param nextCursor 下一页游标（null 表示无更多数据）
     * @param hasMore    是否还有更多数据
     */
    record WishListPage(List<WishListItemVO> records, String nextCursor, boolean hasMore) {}

    /**
     * 我的心愿列表分页结果（cursor 分页）。
     */
    record MyWishListPage(List<MyWishListItemVO> records, String nextCursor, boolean hasMore) {}

    /**
     * OVERDUE 状态机扫描（文档 1.2 定时任务：每日 00:30 由 mall-job 触发）。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>流转条件：status=ACTIVE + expected_at &lt; 当前时间 + 未软删</li>
     *   <li>流转动作：ACTIVE → OVERDUE（仅服务端定时任务可改 status，铁律 39.1）</li>
     *   <li>分批处理（500 条/批），批间无事务关联（每批独立提交，
     *       部分成功不影响其余批次，重复扫描幂等跳过已流转记录）</li>
     *   <li>OVERDUE 提醒推送：通知中心对接前仅记日志占位（同 BADGE_EARNED 口径）</li>
     * </ul>
     *
     * @return 本次扫描流转的心愿总数
     */
    int scanOverdueWishes();

    /**
     * OVERDUE 扫描（详细版，Sprint 2.5）：流转同时返回详情，
     * 供预期管理 AI 引导通知使用（文档 2.5：Sprint 1.1 状态流转 +
     * 2.5 AI 介入复用同一 00:30 任务）。
     */
    OverdueScanResult scanOverdueWishesDetailed();

    /**
     * 到期扫描结果。
     *
     * @param transferred 流转总数
     * @param wishes      本次流转的心愿详情（userId 分组限频在通知侧处理）
     */
    record OverdueScanResult(int transferred, List<OverdueWishInfo> wishes) {
    }

    /**
     * 刚流转为 OVERDUE 的心愿信息（预期管理 AI 引导通知输入）。
     */
    record OverdueWishInfo(Long wishId, Long userId, String title,
                           java.time.LocalDateTime expectedAt) {
    }

    // ---------------- Sprint 1.3 打卡与成长记录 ----------------

    /**
     * 打卡（每日一次，uk_checkin_daily 幂等）：更新连续打卡天数 +
     * 发放星光 +2（CHECKIN 流水）+ 更新 total_checkin_days。
     *
     * @return 打卡结果（含连续天数/星光入账）
     */
    CheckinResultVO checkinWish(Long userId, Long wishId, String content, String mood);

    /**
     * 添加成长记录（TEXT/IMAGE/VIDEO/DIARY），可选进度增量
     * （乐观锁 version 防并发覆盖）。
     */
    GrowthRecordVO addGrowthRecord(Long userId, Long wishId, AddGrowthRequest request);

    /** 查询心愿进度详情 */
    ProgressDetail getWishProgress(Long wishId);

    record CheckinResultVO(Long checkinId, int currentStreak, int maxStreak, int starlightCredited) {}
    record GrowthRecordVO(Long recordId, int newCurrentValue) {}

    /** 成长记录完整时间轴分页（GET /wish/wishes/{id}/growth-records） */
    record GrowthTimelinePage(java.util.List<com.cloudmart.wish.vo.WishGrowthRecordVO> records,
                              String nextCursor, boolean hasMore) {}

    /**
     * 成长记录完整时间轴（cursor 分页；可见性与详情内嵌记录一致：is_visible=true）。
     */
    GrowthTimelinePage listGrowthTimeline(Long wishId, String cursor, Integer pageSize);
    record ProgressDetail(int currentValue, int targetValue, int percentage, int version) {}
    record AddGrowthRequest(String type, String content, List<String> mediaUrls, Short progressDelta) {}
    record ProgressUpdateRequest(int currentValue, int version) {}
    record CheckinCalendarVO(List<String> dates) {}

    /** 进度乐观锁更新（作者；version 不符 → WISH_VERSION_CONFLICT 409 + 最新 version） */
    ProgressDetail updateProgress(Long userId, Long wishId, ProgressUpdateRequest request);

    /** 单心愿打卡日历（作者；month=YYYY-MM，返回当月已打卡日期数组） */
    CheckinCalendarVO getCheckinCalendar(Long userId, Long wishId, String month);

    /** 编辑成长记录（作者；content/mediaUrls） */
    GrowthRecordVO updateGrowthRecord(Long userId, Long wishId, Long recordId,
                                      String content, List<String> mediaUrls);

    /** 删除成长记录（作者；进度为历史事实不回退） */
    void deleteGrowthRecord(Long userId, Long wishId, Long recordId);
}

package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.MyWishListQuery;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.dto.WishListQuery;
import com.cloudmart.wish.vo.MyWishListItemVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishDeleteResultVO;
import com.cloudmart.wish.vo.WishListItemVO;
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
}

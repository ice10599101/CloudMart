package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateInteractionRequest;
import com.cloudmart.wish.dto.InteractionListQuery;
import com.cloudmart.wish.vo.InteractionItemVO;
import com.cloudmart.wish.vo.InteractionResultVO;
import com.cloudmart.wish.vo.InteractionRevokeVO;
import com.cloudmart.wish.vo.MyInteractionVO;

import java.util.List;

/**
 * 心愿互动服务接口（文档 2.2 节，Sprint 1.2）。
 *
 * <p>互动规则：</p>
 * <ul>
 *   <li>LIGHT（点亮）：可重复，每次消耗 2 星光（点亮自己心愿同样扣除），
 *       作者获得 +1 星光（每日上限 20，超出不再获得）</li>
 *   <li>SAME_WISH（同求）：每愿望唯一，作者获得 +2 星光（每日上限 50）</li>
 *   <li>BLESS（祝福）：带文字内容，每愿望每日 1 次，无星光变化</li>
 *   <li>ANON_STAR（匿名星光）：Sprint 2.6 启用，本阶段请求返回 400</li>
 *   <li>取消互动：计数回滚，已消耗/已发放星光均不退还（文档 6.1 取消规则）</li>
 * </ul>
 */
public interface InteractionService {

    /**
     * 发起互动。
     *
     * @param userId  当前用户 ID
     * @param wishId  心愿 ID
     * @param request 互动请求
     * @return 互动结果（含心愿最新计数）
     */
    InteractionResultVO createInteraction(Long userId, Long wishId, CreateInteractionRequest request);

    /**
     * 取消互动（按 interactionId 定位，仅可取消自己的互动）。
     *
     * <p>计数回滚（light_count/same_wish_count/bless_count -1），
     * 已扣/已发星光不退还；取消同求后允许重新同求（与 DB 函数唯一索引语义一致）。</p>
     *
     * @param userId         当前用户 ID
     * @param wishId         心愿 ID
     * @param interactionId  互动记录 ID
     * @return 撤销结果
     */
    InteractionRevokeVO revokeInteraction(Long userId, Long wishId, Long interactionId);

    /**
     * 互动列表（cursor 分页，时间倒序）。
     *
     * @param wishId 心愿 ID
     * @param viewerId 查看者 ID（PRIVATE/TREE_HOLE 心愿非作者 404）
     * @param query  查询参数
     * @return 分页结果
     */
    InteractionPage listInteractions(Long wishId, Long viewerId, InteractionListQuery query);

    /**
     * 我的互动状态：当前用户在该心愿上的全部未删除互动记录（时间倒序）。
     *
     * <p>前端用途：SAME_WISH 存在即"已同求"（携带 id 供取消）；
     * BLESS 存在 {@code createdToday=true} 记录即今日已祝福（按钮禁用）；
     * LIGHT 记录仅用于展示累计次数（可重复互动，无禁用语义）。</p>
     *
     * @param userId 当前用户 ID
     * @param wishId 心愿 ID
     * @return 互动记录列表（无互动时为空列表）
     */
    List<MyInteractionVO> listMyInteractions(Long userId, Long wishId);

    /**
     * 互动列表分页结果。
     *
     * @param records    当前页记录
     * @param nextCursor 下一页游标（无更多时为 null）
     * @param hasMore    是否还有更多
     */
    record InteractionPage(List<InteractionItemVO> records, String nextCursor, boolean hasMore) {
    }
}

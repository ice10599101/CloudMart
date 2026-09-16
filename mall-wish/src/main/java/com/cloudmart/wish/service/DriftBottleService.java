package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.BottleCommentRequest;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.vo.DriftBottleCandidateWishVO;
import com.cloudmart.wish.vo.DriftBottleCommentVO;
import com.cloudmart.wish.vo.DriftBottleVO;

import java.util.List;

/**
 * 漂流瓶服务：投瓶 / 捞瓶 / 我的漂流瓶 / 匿名回应 / 瓶下评论树。
 *
 * <p>漂流瓶匿名随机漂流（非 LBS）：投瓶人投出后进入全局海面池，捞起者随机捞取；
 * 支持实名投瓶（捞起者可见身份）与瓶下评论（默认匿名，可切换实名），
 * 评论仅投瓶人与捞起人可见、可评。</p>
 */
public interface DriftBottleService {

    /** 投瓶（content 与 wishId 二选一），返回新漂流瓶（role=THROWN） */
    DriftBottleVO throwBottle(Long userId, ThrowBottleRequest request);

    /** 可关联心愿候选：我最近发布的至多 20 个可关联心愿（公开进行中），id 倒序 */
    List<DriftBottleCandidateWishVO> listCandidateWishes(Long userId);

    /** 捞瓶：随机捞取一个非自己的漂流瓶；海里无瓶返回 null */
    DriftBottleVO fishBottle(Long userId);

    /** 我的漂流瓶（我投出的 + 我捞到的，倒序），含评论数与实名投瓶人信息 */
    List<DriftBottleVO> listMine(Long userId);

    /** 匿名回应（仅捞起人可回应关联心愿漂流瓶）：BLESS 免费 / LIGHT 扣星光 2 点亮 */
    DriftBottleVO interact(Long userId, Long bottleId, String type);

    /**
     * 评论列表：仅投瓶人或捞起人可见；id 倒序游标分页。
     */
    CommentPage listComments(Long userId, Long bottleId, String cursor, Integer pageSize);

    /** 发表评论/回复（仅投瓶人或捞起人）；parentId 指向同一漂流瓶下的评论 */
    DriftBottleCommentVO addComment(Long userId, Long bottleId, BottleCommentRequest request);

    /**
     * 评论分页结果。
     *
     * @param records    当前页记录（按 id 倒序）
     * @param nextCursor 下一页游标（无更多为 null）
     * @param hasMore    是否还有更多
     */
    record CommentPage(List<DriftBottleCommentVO> records, String nextCursor, boolean hasMore) {
    }
}
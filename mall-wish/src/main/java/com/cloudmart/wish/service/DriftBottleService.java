package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.BottleCommentRequest;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.vo.DriftBottleCandidateWishVO;
import com.cloudmart.wish.vo.DriftBottleCommentVO;
import com.cloudmart.wish.vo.DriftBottleQuotaVO;
import com.cloudmart.wish.vo.DriftBottleVO;

import java.util.List;

/**
 * 漂流瓶服务：投瓶 / 捞瓶 / 扔回海里 / 收藏 / 匿名开关 / 我的漂流瓶 / 匿名回应 / 瓶下评论树。
 *
 * <p>漂流瓶匿名随机漂流（非 LBS）：投瓶人投出后进入全局海面池，捞起者随机捞取；
 * 投瓶人与捞瓶人各自可设置是否匿名（未设置默认匿名），双方均实名时才可互见身份并
 * 私聊/查看资料；支持扔回海里（回到海面可再被捞起）、捞起人收藏。
 * 每日配额：投瓶 10 个/天、打捞 20 次/天（UTC 自然日）。</p>
 */
public interface DriftBottleService {

    /** 每日投瓶上限（个/天） */
    int DAILY_THROW_LIMIT = 10;

    /** 每日打捞上限（次/天） */
    int DAILY_FISH_LIMIT = 20;

    /** 可关联心愿候选条数（最近发布的可关联心愿） */
    int CANDIDATE_WISH_LIMIT = 30;

    /** 今日配额查询（页面展示 投瓶 X/10 · 打捞 Y/20） */
    DriftBottleQuotaVO getQuota(Long userId);

    /** 投瓶（content 与 wishId 二选一），返回新漂流瓶（role=THROWN）；每日 10 个上限 */
    DriftBottleVO throwBottle(Long userId, ThrowBottleRequest request);

    /** 可关联心愿候选：我最近发布的至多 30 个可关联心愿（公开进行中），id 倒序 */
    List<DriftBottleCandidateWishVO> listCandidateWishes(Long userId);

    /** 捞瓶：随机捞取一个非自己的漂流瓶（含被扔回海里的）；海里无瓶返回 null；每日 20 次上限 */
    DriftBottleVO fishBottle(Long userId);

    /**
     * 宠物代主人打捞（B11 幂等）：requestId 为稳定业务请求标识——相同标识重入返回
     * 原瓶子结果（不二次抢瓶、不二次计配额）；requestId 为空时退化为普通打捞。
     */
    DriftBottleVO fishBottleForPet(Long userId, String requestId);

    /** 我的漂流瓶（我投出的 + 我捞到的，倒序），含评论数与双方实名身份信息 */
    List<DriftBottleVO> listMine(Long userId);

    /** 我收藏的漂流瓶（捞起人视角，倒序）：个人页收藏面板数据源 */
    List<DriftBottleVO> listCollected(Long userId);

    /** 扔回海里（仅捞起人，PICKED → RETURNED）：回到海面可再被捞起，收藏与捞起人清空 */
    void returnBottle(Long userId, Long bottleId);

    /** 收藏漂流瓶（仅捞起人，仅 PICKED 状态；幂等） */
    DriftBottleVO collectBottle(Long userId, Long bottleId);

    /** 捞瓶人匿名开关（仅捞起人，仅 PICKED 状态；默认匿名） */
    DriftBottleVO updatePickerAnonymity(Long userId, Long bottleId, boolean isAnonymous);

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

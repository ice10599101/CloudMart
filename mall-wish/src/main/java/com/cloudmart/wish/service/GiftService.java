package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AdminGiftRequest;
import com.cloudmart.wish.dto.SendGiftRequest;
import com.cloudmart.wish.vo.GiftRecordPageVO;
import com.cloudmart.wish.vo.GiftRecordVO;
import com.cloudmart.wish.vo.GiftSummaryVO;
import com.cloudmart.wish.vo.GiftVO;
import com.cloudmart.wish.vo.SendGiftResultVO;

import java.util.List;

/**
 * 全站虚拟礼物服务。
 *
 * <p>礼物目录由管理后台维护；用户在心愿详情/社区帖子/直播间用星光送礼。
 * 送礼为星光消费凭证：记录快照化，取消/退款不删除记录。</p>
 */
public interface GiftService {

    /**
     * 上架礼物目录（用户端礼物选择器）。
     *
     * @return 上架礼物列表（sort 升序）
     */
    List<GiftVO> listOnShelfGifts();

    /**
     * 送礼物。
     *
     * <p>事务体：送礼记录落库（快照）→ 星光扣减（同事务，余额不足抛
     * WISH_STARLIGHT_INSUFFICIENT）；直播场景送礼成功后广播礼物特效
     * （事务提交后，广播失败不影响送礼结果）。</p>
     *
     * @param userId  送礼人用户 ID
     * @param request 送礼请求
     * @return 送礼结果（含送礼后余额）
     */
    SendGiftResultVO sendGift(Long userId, SendGiftRequest request);

    /**
     * 我的礼物资产总览（送/收两方向累计件数与星光；礼物为即时消费，无库存语义）。
     */
    GiftSummaryVO getMyGiftSummary(Long userId);

    /**
     * 我送出的礼物记录（id 倒序 cursor 分页）。
     *
     * @param userId   用户 ID
     * @param cursor   游标（上一页末条记录 ID，null = 第一页）
     * @param pageSize 页大小（默认 20，上限 50）
     */
    GiftRecordPageVO listSentRecords(Long userId, Long cursor, Integer pageSize);

    /**
     * 我收到的礼物记录（id 倒序 cursor 分页）。
     */
    GiftRecordPageVO listReceivedRecords(Long userId, Long cursor, Integer pageSize);

    /**
     * 场景礼物墙：某心愿/帖子/直播间的最新送礼记录（id 倒序 cursor 分页）。
     *
     * @param targetType 场景：WISH / POST / LIVE_ROOM
     * @param targetId   场景对象 ID
     */
    GiftRecordPageVO listTargetRecords(Long viewerId, String targetType, Long targetId, Long cursor, Integer pageSize);

    /**
     * 管理端：全量礼物目录（含下架，sort 升序）。
     */
    List<GiftVO> adminListGifts();

    /**
     * 管理端：新增礼物。
     */
    GiftVO adminCreateGift(AdminGiftRequest request);

    /**
     * 管理端：编辑礼物。
     */
    GiftVO adminUpdateGift(Long giftId, AdminGiftRequest request);

    /**
     * 管理端：上架/下架礼物。
     */
    GiftVO adminUpdateGiftStatus(Long giftId, boolean onShelf);

    /**
     * 管理端：软删礼物（保留审计轨迹）。
     */
    void adminDeleteGift(Long giftId);

    /**
     * 管理端：送礼记录列表（offset 分页适配管理表格，可按送礼人/收礼人/场景筛选）。
     *
     * @param senderId   送礼人筛选（可空）
     * @param receiverId 收礼人筛选（可空）
     * @param targetType 场景筛选（可空）
     * @param page       页码（默认 1）
     * @param pageSize   页大小（默认 20，上限 100）
     */
    List<GiftRecordVO> adminListRecords(Long senderId, Long receiverId,
                                        String targetType, Long targetId,
                                        Integer page, Integer pageSize);
}

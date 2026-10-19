package com.cloudmart.software.service;

import java.util.List;
import java.util.Map;

/**
 * UX05：订阅更新流与已读管理。
 * 更新流复用 SF05 定向投递行（不建第二套"关注更新"关系）；
 * 阅读状态只影响未读展示，不影响投递事实；重复标记已读幂等。
 */
public interface SoftwareUpdateFeedService {

    /** 我的订阅列表（含偏好与未读数；退订软件不出现但历史更新保留在更新流） */
    List<Map<String, Object>> mySubscriptions(Long userId);

    /**
     * 更新流（游标分页）：cursor=上一页最后一条 deliveryId（0 或缺省=从头）；
     * 每条含变更摘要（changelog/版本/发布时间/已读状态/软件当前可见性）。
     */
    Map<String, Object> myUpdates(Long userId, long cursor, int size);

    /** 批量标记已读（≤50 条；重复标记幂等）；返回实际新标记数 */
    int markRead(Long userId, List<Long> deliveryIds);

    /** 未读数（未读仅统计 SENT 投递） */
    long unreadCount(Long userId);
}

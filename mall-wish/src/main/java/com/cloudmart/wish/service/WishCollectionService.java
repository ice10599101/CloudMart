package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.WishCollection;

import java.util.List;

/**
 * 心愿收藏服务（Sprint 1.5/3.6 补齐，文档 2.12）。
 */
public interface WishCollectionService {

    /** 收藏心愿（uk 幂等） */
    WishCollection collect(Long userId, Long wishId);

    /** 取消收藏（软删） */
    void uncollect(Long userId, Long wishId);

    /** 收藏列表（含心愿标题/作者/类型，游标分页） */
    List<WishCollectionItemVO> listCollections(Long userId, String cursor, int pageSize);

    /** 单心愿收藏状态查询（详情页收藏按钮回显） */
    boolean isCollected(Long userId, Long wishId);

    /** 收藏条目 VO（含心愿信息） */
    record WishCollectionItemVO(
            Long collectionId, Long wishId, String title,
            String authorNickname, String fruitType, String collectedAt) {
    }
}

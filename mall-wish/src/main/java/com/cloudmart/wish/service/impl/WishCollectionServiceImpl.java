package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCollection;
import com.cloudmart.wish.repository.WishCollectionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.WishCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 心愿收藏服务实现（Sprint 1.5/3.6 补齐，文档 2.12）。
 *
 * <p>wish_collection 表无逻辑删除字段（deletedAt 不在实体中），
 * 取消收藏使用物理 DELETE——数据为非关键书签，硬删合理。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishCollectionServiceImpl implements WishCollectionService {

    private final WishCollectionMapper collectionMapper;
    private final WishMapper wishMapper;

    @Override
    @Transactional
    public WishCollection collect(Long userId, Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        if (wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "不能收藏自己的心愿");
        }
        WishCollection collection = new WishCollection();
        collection.setUserId(userId);
        collection.setWishId(wishId);
        collection.setCollectedAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            collectionMapper.insert(collection);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已收藏过该心愿");
        }
        return collection;
    }

    @Override
    public boolean isCollected(Long userId, Long wishId) {
        return collectionMapper.selectCount(new LambdaQueryWrapper<WishCollection>()
                .eq(WishCollection::getUserId, userId)
                .eq(WishCollection::getWishId, wishId)) > 0;
    }

    @Override
    @Transactional
    public void uncollect(Long userId, Long wishId) {
        collectionMapper.delete(new LambdaQueryWrapper<WishCollection>()
                .eq(WishCollection::getUserId, userId)
                .eq(WishCollection::getWishId, wishId));
    }

    @Override
    public List<WishCollectionItemVO> listCollections(Long userId, String cursor, int pageSize) {
        LambdaQueryWrapper<WishCollection> query = new LambdaQueryWrapper<WishCollection>()
                .eq(WishCollection::getUserId, userId);
        if (cursor != null && !cursor.isBlank()) {
            try {
                query.lt(WishCollection::getId, Long.parseLong(cursor));
            } catch (NumberFormatException ignored) { }
        }
        query.orderByDesc(WishCollection::getId);
        List<WishCollection> collections = collectionMapper.selectList(
                query.last("LIMIT " + Math.min(Math.max(1, pageSize), 100)));

        List<WishCollectionItemVO> result = new ArrayList<>();
        for (WishCollection collection : collections) {
            Wish wish = wishMapper.selectById(collection.getWishId());
            if (wish == null) continue;
            result.add(new WishCollectionItemVO(
                    collection.getId(),
                    wish.getId(),
                    wish.getTitle(),
                    wish.getGeohash() != null ? wish.getGeohash() : "",
                    wish.getFruitType() != null ? wish.getFruitType().name() : "GLOW",
                    collection.getCollectedAt() != null ? collection.getCollectedAt().toString() : ""));
        }
        return result;
    }
}

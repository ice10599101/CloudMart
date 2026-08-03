package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.Tag;
import com.cloudmart.community.entity.TagSubscription;
import com.cloudmart.community.repository.TagMapper;
import com.cloudmart.community.repository.TagSubscriptionMapper;
import com.cloudmart.community.service.TagSubscriptionService;
import com.cloudmart.community.vo.TagVO;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class TagSubscriptionServiceImpl implements TagSubscriptionService {

    private final TagSubscriptionMapper tagSubscriptionMapper;
    private final TagMapper tagMapper;

    public TagSubscriptionServiceImpl(TagSubscriptionMapper tagSubscriptionMapper,
                                      TagMapper tagMapper) {
        this.tagSubscriptionMapper = tagSubscriptionMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    @Transactional
    public void subscribe(Long userId, Long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("TAG_NOT_FOUND", "话题不存在");
        }

        Long existing = tagSubscriptionMapper.selectCount(
                new LambdaQueryWrapper<TagSubscription>()
                        .eq(TagSubscription::getUserId, userId)
                        .eq(TagSubscription::getTagId, tagId)
        );
        if (existing > 0) {
            return;
        }

        TagSubscription sub = new TagSubscription();
        sub.setUserId(userId);
        sub.setTagId(tagId);
        tagSubscriptionMapper.insert(sub);
    }

    @Override
    @Transactional
    public void unsubscribe(Long userId, Long tagId) {
        tagSubscriptionMapper.delete(
                new LambdaQueryWrapper<TagSubscription>()
                        .eq(TagSubscription::getUserId, userId)
                        .eq(TagSubscription::getTagId, tagId)
        );
    }

    @Override
    public boolean isSubscribed(Long userId, Long tagId) {
        if (userId == null) {
            return false;
        }
        return tagSubscriptionMapper.selectCount(
                new LambdaQueryWrapper<TagSubscription>()
                        .eq(TagSubscription::getUserId, userId)
                        .eq(TagSubscription::getTagId, tagId)
        ) > 0;
    }

    @Override
    public List<TagVO> getSubscribedTags(Long userId) {
        List<TagSubscription> subs = tagSubscriptionMapper.selectList(
                new LambdaQueryWrapper<TagSubscription>()
                        .eq(TagSubscription::getUserId, userId)
                        .orderByDesc(TagSubscription::getCreatedAt)
        );
        if (subs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tagIds = subs.stream().map(TagSubscription::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds).stream()
                .map(tag -> new TagVO(
                        tag.getId(), tag.getName(), tag.getIcon(),
                        tag.getPostCount(), tag.getIsHot(), tag.getStatus(), tag.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public List<Long> getSubscriberUserIds(Long tagId) {
        List<TagSubscription> subs = tagSubscriptionMapper.selectList(
                new LambdaQueryWrapper<TagSubscription>()
                        .eq(TagSubscription::getTagId, tagId)
        );
        return subs.stream().map(TagSubscription::getUserId).toList();
    }
}

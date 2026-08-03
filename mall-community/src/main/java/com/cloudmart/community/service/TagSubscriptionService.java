package com.cloudmart.community.service;

import com.cloudmart.community.vo.TagVO;

import java.util.List;

public interface TagSubscriptionService {

    void subscribe(Long userId, Long tagId);

    void unsubscribe(Long userId, Long tagId);

    boolean isSubscribed(Long userId, Long tagId);

    List<TagVO> getSubscribedTags(Long userId);

    List<Long> getSubscriberUserIds(Long tagId);
}

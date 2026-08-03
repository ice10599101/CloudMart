package com.cloudmart.community.service;

import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.TagVO;

import java.util.List;
import java.util.Optional;

public interface CommunityCacheService {

    Optional<PostVO> getPostDetail(Long postId);

    void putPostDetail(Long postId, PostVO postVO);

    void evictPostDetail(Long postId);

    Optional<List<PostVO>> getFeedPosts(String tab, int page, int size);

    void putFeedPosts(String tab, int page, int size, List<PostVO> posts);

    void evictFeedPosts();

    Optional<List<TagVO>> getTrendingTopics(int limit);

    void putTrendingTopics(int limit, List<TagVO> topics);

    void evictTrendingTopics();

    Optional<List<String>> getHotSearches(int limit);

    void putHotSearches(int limit, List<String> searches);

    void evictHotSearches();
}

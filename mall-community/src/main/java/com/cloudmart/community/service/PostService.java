package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreatePostRequest;
import com.cloudmart.community.dto.UpdatePostRequest;
import com.cloudmart.community.vo.PostVO;

public interface PostService {

    PostVO createPost(Long userId, CreatePostRequest request);

    PostVO updatePost(Long userId, Long postId, UpdatePostRequest request);

    void deletePost(Long userId, Long postId);

    PostVO getPostDetail(Long postId, Long currentUserId);

    Page<PostVO> getFeedPosts(String tab, int page, int size, Long currentUserId);

    Page<PostVO> getUserPosts(Long userId, int page, int size, Long currentUserId);

    Page<PostVO> getUserDrafts(Long userId, int page, int size);

    Page<PostVO> searchPosts(String keyword, int page, int size, Long currentUserId);

    Page<PostVO> getPostsByTag(Long tagId, int page, int size, Long currentUserId);

    Page<PostVO> getFollowingFeed(Long userId, int page, int size);

    Page<PostVO> getLikedPosts(Long userId, int page, int size);

    Page<PostVO> getUserCollections(Long userId, int page, int size, Long currentUserId);

    void likePost(Long userId, Long postId);

    void unlikePost(Long userId, Long postId);

    void collectPost(Long userId, Long postId);

    void uncollectPost(Long userId, Long postId);

    Page<PostVO> adminListPosts(String keyword, Integer status, Long userId, int page, int size);

    void adminUpdatePostStatus(Long postId, Integer status);

    void adminToggleTop(Long postId, Boolean isTop);

    Page<PostVO> listPendingReviewPosts(int page, int size);

    void approvePost(Long postId);

    void rejectPost(Long postId, String reason);

    void adminDeletePost(Long postId);
}

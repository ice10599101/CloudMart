package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateCommentRequest;
import com.cloudmart.community.vo.CommentVO;
import com.cloudmart.community.vo.PostCommentVO;

public interface PostCommentService {

    PostCommentVO createComment(Long userId, Long postId, CreateCommentRequest request);

    Page<PostCommentVO> getComments(Long postId, int page, int size, Long currentUserId);

    Page<CommentVO> getMyComments(Long userId, int page, int size);

    void deleteComment(Long userId, Long commentId);

    void likeComment(Long userId, Long commentId);

    void unlikeComment(Long userId, Long commentId);

    Page<PostCommentVO> adminListComments(String keyword, Integer status, int page, int size);

    void adminUpdateCommentStatus(Long commentId, Integer status);

    void adminDeleteComment(Long commentId);
}

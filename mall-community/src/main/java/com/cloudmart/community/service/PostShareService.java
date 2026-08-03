package com.cloudmart.community.service;

import com.cloudmart.community.vo.PostShareVO;

import java.util.List;

public interface PostShareService {

    PostShareVO sharePost(Long userId, Long postId, String channel);

    List<PostShareVO> getPostShares(Long postId, int page, int size);
}

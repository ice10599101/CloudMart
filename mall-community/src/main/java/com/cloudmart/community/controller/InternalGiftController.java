package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.repository.PostMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部-礼物支持接口（全站虚拟礼物，V37 对应前端功能）。
 *
 * <p>供 mall-wish 送礼链路解析帖子归属（作者即收礼人）；仅内部服务调用
 * （mall-wish 经 Feign 代理，hasRole('INTERNAL') 由 X-Internal-Call 头授予）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/gifts")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-礼物支持", description = "帖子归属解析（mall-wish 送礼链路专用）")
public class InternalGiftController {

    private final PostMapper postMapper;

    @GetMapping("/posts/{postId}")
    @Operation(summary = "查询帖子归属", description = "返回 {postId, userId}；帖子不存在/已删除返回 data=null")
    public ApiResponse<Map<String, Object>> getPostOwner(@PathVariable("postId") Long postId) {
        Post post = postMapper.selectOne(new LambdaQueryWrapper<Post>()
                .eq(Post::getId, postId)
                .select(Post::getId, Post::getUserId)
                .last("LIMIT 1"));
        if (post == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(Map.of("postId", post.getId(), "userId", post.getUserId()));
    }
}

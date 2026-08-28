package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.community.dto.CreatePostRequest;
import com.cloudmart.community.entity.Tag;
import com.cloudmart.community.repository.TagMapper;
import com.cloudmart.community.service.PostService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内部内容流转 Controller（Sprint 2.7，mall-wish 还愿内容流转专用）。
 *
 * <p>路由前缀 /internal/content/flow，仅内部服务调用
 * （mall-wish 经 Feign 转发，hasRole('INTERNAL') 由 X-Internal-Call 头授予）。
 * mall-wish 侧字段映射：wish.title → post.title、
 * wish_fulfillment.story → post.content、media_urls → post.mediaUrls；
 * 成就标签"✨ 心愿完成"按名称解析（不存在则创建，文档 2.7：community 负责
 * 标签写入）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/content/flow")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "内部-内容流转", description = "mall-wish 还愿内容流转（社区帖子生成/隐藏）")
public class InternalContentFlowController {

    /** community 帖子状态：2=隐藏（V1 字典） */
    private static final Integer POST_STATUS_HIDDEN = 2;

    private final PostService postService;
    private final TagMapper tagMapper;

    /**
     * 生成传承帖子。
     *
     * @param body {userId, title, content, coverImage?, mediaUrls?, tagNames?}
     * @return {postId}
     */
    @PostMapping("/posts")
    @Operation(summary = "生成传承帖子", description = "标签按名称解析（不存在则创建），帖子默认已发布状态")
    public ApiResponse<Map<String, Object>> createLegacyPost(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String coverImage = (String) body.get("coverImage");
        @SuppressWarnings("unchecked")
        List<String> mediaUrls = (List<String>) body.get("mediaUrls");
        @SuppressWarnings("unchecked")
        List<String> tagNames = (List<String>) body.get("tagNames");

        List<Long> tagIds = resolveTagIds(tagNames);
        CreatePostRequest request = new CreatePostRequest(
                title, content, coverImage, mediaUrls, "IMAGE", null, null, tagIds, null);
        var postVo = postService.createPost(userId, request);
        log.info("内容流转: 传承帖子已生成, userId={}, postId={}, title={}", userId, postVo.id(), title);
        return ApiResponse.ok(Map.of("postId", postVo.id()));
    }

    /**
     * 隐藏帖子（状态同步：还愿故事删除 → 帖子隐藏）。
     */
    @PutMapping("/posts/{id}/hide")
    @Operation(summary = "隐藏传承帖子", description = "帖子状态置为隐藏（2）")
    public ApiResponse<Void> hideLegacyPost(@PathVariable("id") Long postId) {
        postService.adminUpdatePostStatus(postId, POST_STATUS_HIDDEN);
        log.info("内容流转: 传承帖子已隐藏, postId={}", postId);
        return ApiResponse.ok(null);
    }

    /** 标签名 → tagId（不存在则创建；幂等） */
    private List<Long> resolveTagIds(List<String> tagNames) {
        List<Long> tagIds = new ArrayList<>();
        if (tagNames == null) {
            return tagIds;
        }
        for (String name : tagNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, name)
                    .last("LIMIT 1"));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tagMapper.insert(tag);
                log.info("内容流转: 成就标签已创建, name={}", name);
            }
            tagIds.add(tag.getId());
        }
        return tagIds;
    }
}

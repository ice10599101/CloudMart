package com.cloudmart.wish.vo;

/**
 * 心愿作者信息 VO（嵌套对象）。
 *
 * <p>由 Feign 调用 mall-user 服务填充，{@code nickname/avatar} 缺失时降级为占位值，
 * 不阻塞心愿主链路（Fail Open）。</p>
 *
 * @param authorId   作者用户 ID
 * @param nickname   作者昵称（缺失时降级为 "心愿旅人"）
 * @param avatar     作者头像 URL（缺失时为空字符串）
 */
public record AuthorInfoVO(
        Long authorId,
        String nickname,
        String avatar
) {}

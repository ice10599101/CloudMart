package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端精选排序请求（方案 7.5/10）：首页精选推荐 P0 能力，
 * 在“软件管理”中手动选择软件、取消推荐并设置推荐顺序，使用 featured_rank。
 */
@Getter
@Setter
public class AdminFeaturedRequest {

    /** 精选排序（0=取消精选；1~9999，值越大越靠前） */
    private Integer featuredRank;
}

package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 软件广场列表查询（方案 7.2）：筛选写入 URL；sort=recommend/latest/popular（P1 增 rating）。
 * 分页 page 从 1 起，size 默认 20 最大 50；page 过大限制 1000。
 */
@Getter
@Setter
public class SoftwareListQuery {

    private String keyword;

    private Long categoryId;

    /** 平台筛选（枚举名，非法值忽略） */
    private String platform;

    private String license;

    private Long tagId;

    /** recommend/latest/popular/rating（P1；非法值回落 recommend） */
    private String sort = "recommend";

    private Integer page = 1;

    private Integer size = 20;

    public int safePage() {
        if (page == null || page < 1) {
            return 1;
        }
        return Math.min(page, 1000);
    }

    public int safeSize() {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 50);
    }
}

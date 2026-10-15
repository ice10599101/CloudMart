package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端软件目录查询（方案 7.5/10.1）：关键词、分享者、分类、授权类型、状态筛选；
 * 可查看当前线上内容、待审内容、精选与下架记录。
 *
 * <p>status 取值与生命周期对齐：ACTIVE（在架）/PENDING（待审）/FEATURED（精选）/
 * UNLISTED（作者下架）/BANNED（平台封禁）；空=全部（不含已删除）。
 * 分页 page 从 1 起，size 默认 20 最大 100。</p>
 */
@Getter
@Setter
public class AdminItemQuery {

    /** 软件名/简介模糊匹配（线上快照优先，无线上读工作快照） */
    private String keyword;

    /** 分享者（前台会员 ID） */
    private Long ownerId;

    private Long categoryId;

    /** 授权类型 FREE/OPEN_SOURCE/TRIAL/COMMERCIAL */
    private String license;

    /** ACTIVE/PENDING/FEATURED/UNLISTED/BANNED；空=全部 */
    private String status;

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
        return Math.min(size, 100);
    }
}

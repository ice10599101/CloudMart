package com.cloudmart.software.service;

import java.util.List;
import java.util.Map;

/**
 * 软件专题公开读取（方案 6.11，P1）：只显示 PUBLISHED；清单按可见性实时过滤，
 * 不能通过专题读到下架软件。
 */
public interface SoftwareTopicService {

    /** 已上线专题列表（按 sortOrder） */
    List<Map<String, Object>> listPublished();

    /** 专题详情（含实时过滤后的软件卡片清单）；非 PUBLISHED 统一不存在提示 */
    Map<String, Object> publishedDetail(String slug);
}

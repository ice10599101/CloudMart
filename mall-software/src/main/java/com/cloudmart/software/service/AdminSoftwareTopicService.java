package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.AdminTopicSaveRequest;

import java.util.Map;

/**
 * 管理端专题维护（方案 7.5/10.6，P1，权限 software:topic）：
 * 创建/编辑/禁用专题与软件排序项整体维护；DELETE 语义为禁用。
 */
public interface AdminSoftwareTopicService {

    /** 专题分页（含草稿/禁用） */
    Page<Map<String, Object>> page(int page, int size);

    /** 编辑读取：专题 + 原始清单（不过滤可见性） */
    Map<String, Object> detail(Long topicId);

    /** 新增/更新（topicId 空=新增；清单全量替换）；返回专题 ID */
    Long save(Long adminId, Long topicId, String idempotencyKey, AdminTopicSaveRequest request);

    /** 禁用（DELETE 语义，不物理删除） */
    boolean disable(Long adminId, Long topicId, String idempotencyKey);
}

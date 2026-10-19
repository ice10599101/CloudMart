package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.SoftwareListQuery;
import com.cloudmart.software.vo.software.SoftwareAccessPolicyVO;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import com.cloudmart.software.vo.software.SoftwareCategoryVO;
import com.cloudmart.software.vo.software.SoftwareDetailVO;
import com.cloudmart.software.vo.software.SoftwareReleaseVO;
import com.cloudmart.software.vo.software.SoftwareTagVO;

import java.util.List;

/**
 * 公开只读查询（方案 7.2）：全部经 SoftwareAccessPolicy 可见性裁决，
 * 草稿/待审/下架/封禁/删除不会从列表/详情/版本/来源摘要泄漏。
 */
public interface SoftwareQueryService {

    /** 软件广场/筛选结果分页 */
    Page<SoftwareCardVO> pageCards(SoftwareListQuery query);

    /** 按 ID 集取卡片（P1 榜单/专题用）：仅公开可见返回；顺序由调用方决定 */
    List<SoftwareCardVO> cardsByIds(List<Long> softwareIds);

    /** 详情（viewerId 可空：游客个性化字段为 null；登录返回 isFavorited） */
    SoftwareDetailVO detail(Long softwareId, Long viewerId);

    /** UX03：分享者公开投稿分页（仅 ACTIVE+已发布；不含草稿/审核原因/内部权限） */
    Page<com.cloudmart.software.vo.software.SoftwareCardVO> publisherItems(Long publisherUserId,
                                                                           int page, int size);

    /** 启用中的分类（筛选字典） */
    List<SoftwareCategoryVO> categories();

    /** 标签检索（仅启用；keyword 匹配标签名） */
    Page<SoftwareTagVO> pageTags(String keyword, int page, int size);

    /** 当前线上查看条件摘要（无用户授权状态/付款备注/源信息） */
    SoftwareAccessPolicyVO accessPolicy(Long softwareId);

    /** 版本与来源摘要分页（无 URL/提取码；按渠道与 release_order 排序） */
    Page<SoftwareReleaseVO> releases(Long softwareId, int page, int size);
}

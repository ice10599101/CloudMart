package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AdminCreateBadgeRequest;
import com.cloudmart.wish.dto.AdminUpdateBadgeRequest;
import com.cloudmart.wish.vo.AdminBadgeVO;

import java.util.List;

/**
 * 管理端徽章服务（文档 33.4.7：新增/编辑/上下架 + condition JSON 编辑校验）。
 *
 * <p>condition 结构校验复用 {@code BadgeConditionParser.validate}
 * （type/threshold/description 三段式声明式定义）。</p>
 */
public interface AdminBadgeService {

    /**
     * 徽章全量列表（含下架，管理端需查看与操作全部定义）。
     *
     * @return 全部徽章（badgeId 升序），含原始 condition JSON 供编辑器回显
     */
    List<AdminBadgeVO> listBadges();

    /**
     * 新增徽章。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         BADGE_CODE_DUPLICATED（code 唯一冲突，含并发兜底 DuplicateKey）
     *         / BADGE_CONDITION_INVALID（condition 结构校验失败）
     */
    AdminBadgeVO createBadge(AdminCreateBadgeRequest request);

    /**
     * 编辑徽章（code 不可修改，请求中无该字段）。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         BADGE_NOT_FOUND / BADGE_CONDITION_INVALID
     */
    AdminBadgeVO updateBadge(Long badgeId, AdminUpdateBadgeRequest request);

    /**
     * 上/下架。
     *
     * <p>下架语义：立即不参与授予判定、不出现在徽章墙与图鉴；
     * 已获得记录保留（wish_user_badge 不删），重新上架自动恢复展示。</p>
     *
     * @throws com.cloudmart.common.exception.BusinessException BADGE_NOT_FOUND
     */
    AdminBadgeVO updateBadgeStatus(Long badgeId, boolean active);
}

package com.cloudmart.wish.policy;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.WishVisibility;
import org.springframework.stereotype.Component;

/**
 * 心愿宇宙统一访问策略（B02，任务书 4.1 访问矩阵）。
 *
 * <p>所有 Service 入口必须显式传入已认证 viewer（匿名传 null），并经本策略做
 * 资源级授权判断；禁止各入口自行拼装可见性条件。</p>
 *
 * <p>核心不变量：</p>
 * <ul>
 *   <li>私密/树洞/已删除资源对非作者统一按 404 语义拒绝（WISH_NOT_FOUND），
 *       不泄露资源存在性；</li>
 *   <li>DIARY 永远仅作者可读——公开心愿的详情内嵌记录与独立时间轴同样过滤，
 *       解密只能在授权判定之后发生；</li>
 *   <li>公开可读谓词唯一：未删除 + PUBLIC + isVisible + 状态允许 + 审核允许
 *       （LAZY 的 PENDING 可先展示，STRICT 的 PENDING 不公开，
 *       REJECTED/AUTO_HIDDEN 始终不公开）。</li>
 * </ul>
 */
@Component
public class WishAccessPolicy {

    /** 是否作者本人。 */
    public boolean isOwner(Wish wish, Long viewerId) {
        return wish != null && viewerId != null && wish.getUserId().equals(viewerId);
    }

    /**
     * 公共可见谓词（任务书 4.1）：任何匿名/其他用户的公开读路径必须以本判定为准。
     */
    public boolean isPublicReadable(Wish wish) {
        if (wish == null || wish.getDeletedAt() != null) {
            return false;
        }
        if (wish.getVisibility() != WishVisibility.PUBLIC) {
            return false;
        }
        if (!Boolean.TRUE.equals(wish.getIsVisible())) {
            return false;
        }
        AuditStatus audit = wish.getAuditStatus();
        if (audit == AuditStatus.REJECTED || audit == AuditStatus.AUTO_HIDDEN) {
            return false;
        }
        if (audit == AuditStatus.APPROVED) {
            return true;
        }
        // PENDING：先发后审（LAZY）可先展示；STRICT 下未审完不公开
        return audit == AuditStatus.PENDING && wish.getAuditStrategy() == AuditStrategy.LAZY;
    }

    /** viewer 是否可读该心愿（作者或公共可见）。 */
    public boolean isReadableBy(Wish wish, Long viewerId) {
        if (wish == null || wish.getDeletedAt() != null) {
            return false;
        }
        return isOwner(wish, viewerId) || isPublicReadable(wish);
    }

    /**
     * 要求可读；不满足时统一抛 404 语义（不区分"不存在"与"无权查看"）。
     */
    public void requireReadable(Wish wish, Long viewerId) {
        if (!isReadableBy(wish, viewerId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
    }

    /**
     * 要求作者本人（写路径）。
     * 私密/树洞/已删除（不可读）资源对非作者按 404 语义拒绝，不泄露存在性；
     * 公开可读资源对非作者返回 WISH_NOT_AUTHOR（存在性已知，403 更可诊断）。
     */
    public void requireOwner(Wish wish, Long userId) {
        if (wish == null || wish.getDeletedAt() != null || !isReadableBy(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        if (!isOwner(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR, "仅作者可操作此心愿");
        }
    }

    /**
     * DIARY 是否可读：永远仅作者（无论心愿本身可见性）。
     */
    public boolean canReadDiary(Wish wish, Long viewerId) {
        return isOwner(wish, viewerId);
    }

    /**
     * 是否可互动（点亮/同求/祝福/收藏/送礼/评论等新增互动）：
     * 必须公共可见——私密资源不可被互动，被下架/驳回后停止新增互动与消费。
     */
    public boolean canInteract(Wish wish, Long viewerId) {
        return viewerId != null && isPublicReadable(wish);
    }

    /** 要求可互动；不满足时统一 404 语义。 */
    public void requireInteractable(Wish wish, Long viewerId) {
        if (!canInteract(wish, viewerId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
    }
}

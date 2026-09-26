package com.cloudmart.wish.policy;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.WishVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B02 访问策略矩阵测试（任务书 4.1）：作者/其他用户/匿名 ×
 * PUBLIC/PRIVATE/TREE_HOLE × 审核状态 × 软删的判定结果。
 */
@DisplayName("WishAccessPolicy 统一访问矩阵")
class WishAccessPolicyTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private final WishAccessPolicy policy = new WishAccessPolicy();

    private Wish wish(WishVisibility visibility, AuditStatus audit) {
        Wish wish = new Wish();
        wish.setId(100L);
        wish.setUserId(AUTHOR_ID);
        wish.setVisibility(visibility);
        wish.setAuditStatus(audit);
        wish.setAuditStrategy(AuditStrategy.LAZY);
        wish.setIsVisible(true);
        return wish;
    }

    @Test
    @DisplayName("PUBLIC+APPROVED：他人可读可互动；匿名可读不可互动")
    void publicWish_matrix() {
        Wish wish = wish(WishVisibility.PUBLIC, AuditStatus.APPROVED);

        assertThat(policy.isReadableBy(wish, OTHER_ID)).isTrue();
        assertThat(policy.isReadableBy(wish, null)).isTrue();
        assertThat(policy.canInteract(wish, OTHER_ID)).isTrue();
        assertThat(policy.canInteract(wish, null)).isFalse();
    }

    @Test
    @DisplayName("PRIVATE/TREE_HOLE：仅作者可读，他人与匿名一律不可读")
    void privateWish_matrix() {
        for (WishVisibility visibility : new WishVisibility[]{WishVisibility.PRIVATE, WishVisibility.TREE_HOLE}) {
            Wish wish = wish(visibility, AuditStatus.APPROVED);

            assertThat(policy.isReadableBy(wish, AUTHOR_ID)).isTrue();
            assertThat(policy.isReadableBy(wish, OTHER_ID)).isFalse();
            assertThat(policy.isReadableBy(wish, null)).isFalse();
            assertThat(policy.canInteract(wish, OTHER_ID)).isFalse();
        }
    }

    @Test
    @DisplayName("软删心愿任何人（含作者）不可读")
    void deletedWish_neverReadable() {
        Wish wish = wish(WishVisibility.PUBLIC, AuditStatus.APPROVED);
        wish.setDeletedAt(LocalDateTime.now());

        assertThat(policy.isReadableBy(wish, AUTHOR_ID)).isFalse();
        assertThat(policy.isReadableBy(wish, OTHER_ID)).isFalse();
    }

    @Test
    @DisplayName("审核状态：REJECTED/AUTO_HIDDEN 不公开；LAZY 的 PENDING 先展示；STRICT 的 PENDING 不公开")
    void auditStatus_publicPredicate() {
        assertThat(policy.isPublicReadable(wish(WishVisibility.PUBLIC, AuditStatus.REJECTED))).isFalse();
        assertThat(policy.isPublicReadable(wish(WishVisibility.PUBLIC, AuditStatus.AUTO_HIDDEN))).isFalse();

        Wish lazyPending = wish(WishVisibility.PUBLIC, AuditStatus.PENDING);
        assertThat(policy.isPublicReadable(lazyPending)).isTrue();

        lazyPending.setAuditStrategy(AuditStrategy.STRICT);
        assertThat(policy.isPublicReadable(lazyPending)).isFalse();

        assertThat(policy.isPublicReadable(wish(WishVisibility.PUBLIC, AuditStatus.APPROVED))).isTrue();
    }

    @Test
    @DisplayName("isVisible=false（下架）不公开")
    void offShelfWish_notPublic() {
        Wish wish = wish(WishVisibility.PUBLIC, AuditStatus.APPROVED);
        wish.setIsVisible(false);

        assertThat(policy.isPublicReadable(wish)).isFalse();
        assertThat(policy.isReadableBy(wish, AUTHOR_ID)).isTrue();
    }

    @Test
    @DisplayName("requireReadable：私密对非作者抛 404 语义，不泄露存在性")
    void requireReadable_privateForOther_throwsNotFound() {
        Wish wish = wish(WishVisibility.PRIVATE, AuditStatus.APPROVED);

        assertThatThrownBy(() -> policy.requireReadable(wish, OTHER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(com.cloudmart.wish.constant.WishErrorCodes.WISH_NOT_FOUND));
    }

    @Test
    @DisplayName("requireOwner：公开心愿非作者 403（NOT_AUTHOR）；私密心愿非作者 404；作者通过")
    void requireOwner_semantics() {
        Wish publicWish = wish(WishVisibility.PUBLIC, AuditStatus.APPROVED);
        assertThatThrownBy(() -> policy.requireOwner(publicWish, OTHER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(com.cloudmart.wish.constant.WishErrorCodes.WISH_NOT_AUTHOR));
        assertThatCode(() -> policy.requireOwner(publicWish, AUTHOR_ID)).doesNotThrowAnyException();

        Wish privateWish = wish(WishVisibility.PRIVATE, AuditStatus.APPROVED);
        assertThatThrownBy(() -> policy.requireOwner(privateWish, OTHER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(com.cloudmart.wish.constant.WishErrorCodes.WISH_NOT_FOUND));

        assertThatThrownBy(() -> policy.requireOwner(null, AUTHOR_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("DIARY 永远仅作者可读——即使心愿 PUBLIC")
    void canReadDiary_authorOnly() {
        Wish wish = wish(WishVisibility.PUBLIC, AuditStatus.APPROVED);

        assertThat(policy.canReadDiary(wish, AUTHOR_ID)).isTrue();
        assertThat(policy.canReadDiary(wish, OTHER_ID)).isFalse();
        assertThat(policy.canReadDiary(wish, null)).isFalse();
    }
}

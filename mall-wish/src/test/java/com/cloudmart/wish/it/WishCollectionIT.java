package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.service.WishCollectionService;
import com.cloudmart.wish.service.WishCollectionService.WishCollectionItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("心愿收藏集成测试")
class WishCollectionIT extends WishIntegrationTestBase {

    @Autowired
    private WishCollectionService wishCollectionService;

    private static final long USER_A = 960L;
    private static final long USER_B = 961L;

    private long seedWish(long userId, String title) {
        long wishId = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, created_at, updated_at)
                VALUES (?, ?, ?, '测试', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, NOW(), NOW())
                """, wishId, userId, title);
        return wishId;
    }

    @Test
    @DisplayName("收藏→列表→取消收藏→列表消失；不能收藏自己的心愿")
    void collectAndUncollect() {
        long wishId = seedWish(USER_B, "别人的心愿");

        // 收藏
        wishCollectionService.collect(USER_A, wishId);
        var list = wishCollectionService.listCollections(USER_A, null, 20);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).wishId()).isEqualTo(wishId);
        assertThat(list.get(0).title()).isEqualTo("别人的心愿");

        // 不能收藏自己的
        long ownWish = seedWish(USER_A, "自己的心愿");
        assertThatThrownBy(() -> wishCollectionService.collect(USER_A, ownWish))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);

        // 取消收藏
        wishCollectionService.uncollect(USER_A, wishId);
        assertThat(wishCollectionService.listCollections(USER_A, null, 20)).isEmpty();

        // 取消后可重新收藏
        wishCollectionService.collect(USER_A, wishId);
        assertThat(wishCollectionService.listCollections(USER_A, null, 20)).hasSize(1);
    }
}

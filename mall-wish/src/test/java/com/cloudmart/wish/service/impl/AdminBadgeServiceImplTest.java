package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminCreateBadgeRequest;
import com.cloudmart.wish.dto.AdminUpdateBadgeRequest;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.vo.AdminBadgeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * AdminBadgeService 单元测试（文档 33.4.7：condition JSON 编辑校验 + CRUD + 上下架）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBadgeService 单元测试")
class AdminBadgeServiceImplTest {

    private static final String VALID_CONDITION =
            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"发布第一个心愿\"}";

    @Mock
    private WishBadgeMapper wishBadgeMapper;

    @InjectMocks
    private AdminBadgeServiceImpl adminBadgeService;

    private AdminCreateBadgeRequest createRequest(String code, String condition) {
        AdminCreateBadgeRequest request = new AdminCreateBadgeRequest();
        request.setCode(code);
        request.setName("测试徽章");
        request.setIcon("");
        request.setRarity("COMMON");
        request.setCondition(condition);
        return request;
    }

    private AdminUpdateBadgeRequest updateRequest(String condition) {
        AdminUpdateBadgeRequest request = new AdminUpdateBadgeRequest();
        request.setName("改名徽章");
        request.setIcon("");
        request.setRarity("RARE");
        request.setCondition(condition);
        return request;
    }

    private WishBadge existingBadge() {
        WishBadge badge = new WishBadge();
        badge.setId(2001L);
        badge.setCode("FIRST_WISH");
        badge.setName("第一次许愿");
        badge.setRarity("COMMON");
        badge.setIsActive(true);
        badge.setCondition(VALID_CONDITION);
        return badge;
    }

    @Nested
    @DisplayName("createBadge - 新增徽章")
    class CreateBadgeTests {

        @Test
        @DisplayName("condition 结构非法（缺 threshold）：BADGE_CONDITION_INVALID 且不落库")
        void invalidConditionRejected() {
            AdminCreateBadgeRequest request =
                    createRequest("NEW_BADGE", "{\"type\":\"WISH_CREATED\",\"description\":\"缺阈值\"}");

            assertThatThrownBy(() -> adminBadgeService.createBadge(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("threshold");
        }

        @Test
        @DisplayName("type 非法枚举：BADGE_CONDITION_INVALID")
        void invalidTypeRejected() {
            AdminCreateBadgeRequest request =
                    createRequest("NEW_BADGE", "{\"type\":\"NOT_EXIST\",\"threshold\":1,\"description\":\"x\"}");

            assertThatThrownBy(() -> adminBadgeService.createBadge(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("type");
        }

        @Test
        @DisplayName("code 重复（预查）：BADGE_CODE_DUPLICATED")
        void duplicatedCodeRejected() {
            when(wishBadgeMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminBadgeService.createBadge(createRequest("FIRST_WISH", VALID_CONDITION)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FIRST_WISH");
        }

        @Test
        @DisplayName("正常新增：默认上架，字段完整回显")
        void createSuccess() {
            when(wishBadgeMapper.selectCount(any())).thenReturn(0L);

            AdminBadgeVO vo = adminBadgeService.createBadge(createRequest("NEW_BADGE", VALID_CONDITION));

            assertThat(vo.getCode()).isEqualTo("NEW_BADGE");
            assertThat(vo.getIsActive()).isTrue();
            assertThat(vo.getCondition()).isEqualTo(VALID_CONDITION);
        }

        @Test
        @DisplayName("并发同 code 插入：DuplicateKeyException 转业务异常")
        void concurrentDuplicateKeyHandled() {
            when(wishBadgeMapper.selectCount(any())).thenReturn(0L);
            when(wishBadgeMapper.insert(any(WishBadge.class)))
                    .thenThrow(new DuplicateKeyException("uk_badge_code"));

            assertThatThrownBy(() -> adminBadgeService.createBadge(createRequest("NEW_BADGE", VALID_CONDITION)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在");
        }
    }

    @Nested
    @DisplayName("updateBadge - 编辑徽章")
    class UpdateBadgeTests {

        @Test
        @DisplayName("徽章不存在：BADGE_NOT_FOUND")
        void badgeNotFound() {
            when(wishBadgeMapper.selectById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> adminBadgeService.updateBadge(9999L, updateRequest(VALID_CONDITION)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("徽章不存在");
        }

        @Test
        @DisplayName("condition 非法 JSON：BADGE_CONDITION_INVALID（编辑校验）")
        void malformedConditionRejected() {
            assertThatThrownBy(() -> adminBadgeService.updateBadge(2001L, updateRequest("not-json{")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("合法 JSON");
        }

        @Test
        @DisplayName("正常编辑：名称/稀有度/condition 更新，code 不变")
        void updateSuccess() {
            WishBadge badge = existingBadge();
            when(wishBadgeMapper.selectById(2001L)).thenReturn(badge);

            AdminBadgeVO vo = adminBadgeService.updateBadge(2001L,
                    updateRequest("{\"type\":\"TOTAL_HELPED\",\"threshold\":50,\"description\":\"帮助50人\"}"));

            assertThat(vo.getName()).isEqualTo("改名徽章");
            assertThat(vo.getRarity()).isEqualTo("RARE");
            assertThat(vo.getCode()).isEqualTo("FIRST_WISH");
        }
    }

    @Nested
    @DisplayName("updateBadgeStatus - 上下架")
    class StatusTests {

        @Test
        @DisplayName("下架：is_active=false，重新上架恢复 true")
        void toggleStatus() {
            WishBadge badge = existingBadge();
            when(wishBadgeMapper.selectById(2001L)).thenReturn(badge);

            assertThat(adminBadgeService.updateBadgeStatus(2001L, false).getIsActive()).isFalse();
            assertThat(adminBadgeService.updateBadgeStatus(2001L, true).getIsActive()).isTrue();
        }

        @Test
        @DisplayName("徽章不存在：BADGE_NOT_FOUND")
        void statusBadgeNotFound() {
            when(wishBadgeMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> adminBadgeService.updateBadgeStatus(9999L, true))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode())
                            .isEqualTo(WishErrorCodes.BADGE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listBadges - 全量列表")
    class ListTests {

        @Test
        @DisplayName("含下架徽章，condition 原文回显供编辑器")
        void listIncludesInactive() {
            WishBadge active = existingBadge();
            WishBadge inactive = existingBadge();
            inactive.setId(2002L);
            inactive.setIsActive(false);
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(active, inactive));

            List<AdminBadgeVO> list = adminBadgeService.listBadges();

            assertThat(list).hasSize(2);
            assertThat(list.get(0).getIsActive()).isTrue();
            assertThat(list.get(1).getIsActive()).isFalse();
            assertThat(list.get(0).getCondition()).isEqualTo(VALID_CONDITION);
        }
    }
}

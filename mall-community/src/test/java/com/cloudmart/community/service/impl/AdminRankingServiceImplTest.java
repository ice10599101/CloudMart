package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.RankingSeason;
import com.cloudmart.community.repository.RankingSeasonMapper;
import com.cloudmart.community.vo.RankingSeasonVO;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRankingServiceImplTest {

    @Mock
    private RankingSeasonMapper rankingSeasonMapper;

    private AdminRankingServiceImpl adminRankingService;

    @BeforeEach
    void setUp() {
        adminRankingService = new AdminRankingServiceImpl(rankingSeasonMapper);
    }

    private RankingSeason buildSeason(Long id, String seasonKey, int status) {
        RankingSeason season = new RankingSeason();
        season.setId(id);
        season.setName("2026年" + seasonKey.substring(4) + "月经验榜");
        season.setSeasonKey(seasonKey);
        season.setStartDate(LocalDate.of(2026, 1, 1));
        season.setEndDate(LocalDate.of(2026, 1, 31));
        season.setStatus(status);
        return season;
    }

    // ======================== listSeasons ========================

    @Nested
    @DisplayName("listSeasons")
    class ListSeasonsTests {

        @Test
        @DisplayName("should return paginated seasons without status filter")
        void listSeasons_noStatusFilter() {
            RankingSeason season1 = buildSeason(1L, "202607", 1);
            RankingSeason season2 = buildSeason(2L, "202606", 1);
            Page<RankingSeason> page = new Page<>(1, 10, 2);
            page.setRecords(List.of(season1, season2));
            when(rankingSeasonMapper.selectPage(any(Page.class), any())).thenReturn(page);

            Page<RankingSeasonVO> result = adminRankingService.listSeasons(1, 10, null);

            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getRecords().get(0).seasonKey()).isEqualTo("202607");
            assertThat(result.getRecords().get(1).seasonKey()).isEqualTo("202606");
            assertThat(result.getTotal()).isEqualTo(2);
        }

        @Test
        @DisplayName("should filter by status when provided")
        void listSeasons_withStatusFilter() {
            RankingSeason season = buildSeason(1L, "202607", 0);
            Page<RankingSeason> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(season));
            when(rankingSeasonMapper.selectPage(any(Page.class), any())).thenReturn(page);

            Page<RankingSeasonVO> result = adminRankingService.listSeasons(1, 10, 0);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).status()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return empty page when no data")
        void listSeasons_empty() {
            Page<RankingSeason> page = new Page<>(1, 10, 0);
            page.setRecords(List.of());
            when(rankingSeasonMapper.selectPage(any(Page.class), any())).thenReturn(page);

            Page<RankingSeasonVO> result = adminRankingService.listSeasons(1, 10, null);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
        }
    }

    // ======================== updateSeasonStatus ========================

    @Nested
    @DisplayName("updateSeasonStatus")
    class UpdateSeasonStatusTests {

        @Test
        @DisplayName("should update season status successfully")
        void updateSeasonStatus_success() {
            RankingSeason season = buildSeason(1L, "202607", 0);
            when(rankingSeasonMapper.selectById(1L)).thenReturn(season);

            adminRankingService.updateSeasonStatus(1L, 1);

            ArgumentCaptor<RankingSeason> captor = ArgumentCaptor.forClass(RankingSeason.class);
            verify(rankingSeasonMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw when season not found")
        void updateSeasonStatus_notFound_throwsException() {
            when(rankingSeasonMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminRankingService.updateSeasonStatus(999L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("SEASON_NOT_FOUND");
                    });

            verify(rankingSeasonMapper, never()).updateById(any(RankingSeason.class));
        }

        @Test
        @DisplayName("should throw when status is invalid")
        void updateSeasonStatus_invalidStatus_throwsException() {
            assertThatThrownBy(() -> adminRankingService.updateSeasonStatus(1L, 5))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("INVALID_STATUS");
                    });

            verify(rankingSeasonMapper, never()).selectById(any());
            verify(rankingSeasonMapper, never()).updateById(any(RankingSeason.class));
        }

        @Test
        @DisplayName("should throw when status is null")
        void updateSeasonStatus_nullStatus_throwsException() {
            assertThatThrownBy(() -> adminRankingService.updateSeasonStatus(1L, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("INVALID_STATUS");
                    });

            verify(rankingSeasonMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("should accept status 0 (in progress)")
        void updateSeasonStatus_statusZero() {
            RankingSeason season = buildSeason(1L, "202607", 1);
            when(rankingSeasonMapper.selectById(1L)).thenReturn(season);

            adminRankingService.updateSeasonStatus(1L, 0);

            ArgumentCaptor<RankingSeason> captor = ArgumentCaptor.forClass(RankingSeason.class);
            verify(rankingSeasonMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(0);
        }
    }
}

package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.dto.CreateLevelConfigRequest;
import com.cloudmart.community.dto.UpdateLevelConfigRequest;
import com.cloudmart.community.entity.DailyCheckIn;
import com.cloudmart.community.entity.LevelConfig;
import com.cloudmart.community.repository.DailyCheckInMapper;
import com.cloudmart.community.repository.LevelConfigMapper;
import com.cloudmart.community.vo.LevelConfigVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminGrowthServiceImplTest {

    @Mock
    private LevelConfigMapper levelConfigMapper;

    @Mock
    private DailyCheckInMapper dailyCheckInMapper;

    private AdminGrowthServiceImpl adminGrowthService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant1 = new MapperBuilderAssistant(configuration, "");
        assistant1.setCurrentNamespace("com.cloudmart.community.repository.LevelConfigMapper");
        TableInfoHelper.initTableInfo(assistant1, LevelConfig.class);
        MapperBuilderAssistant assistant2 = new MapperBuilderAssistant(configuration, "");
        assistant2.setCurrentNamespace("com.cloudmart.community.repository.DailyCheckInMapper");
        TableInfoHelper.initTableInfo(assistant2, DailyCheckIn.class);
    }

    @BeforeEach
    void setUp() {
        adminGrowthService = new AdminGrowthServiceImpl(levelConfigMapper, dailyCheckInMapper);
    }

    private LevelConfig buildLevelConfig(Long id, int level, String title, int minExp) {
        LevelConfig config = new LevelConfig();
        config.setId(id);
        config.setLevel(level);
        config.setTitle(title);
        config.setMinExp(minExp);
        config.setIcon("icon.png");
        config.setBenefits("benefits");
        config.setStatus(1);
        return config;
    }

    @Nested
    @DisplayName("createLevelConfig")
    class CreateLevelConfigTests {

        @Test
        @DisplayName("should create level config successfully")
        void createLevelConfig_success() {
            CreateLevelConfigRequest request = new CreateLevelConfigRequest(3, "高级会员", 1000, "star.png", "专属权益");
            when(levelConfigMapper.selectCount(any())).thenReturn(0L);
            when(levelConfigMapper.insert(any(LevelConfig.class))).thenAnswer(invocation -> {
                LevelConfig config = invocation.getArgument(0);
                config.setId(1L);
                return 1;
            });

            LevelConfigVO result = adminGrowthService.createLevelConfig(request);

            assertThat(result).isNotNull();
            assertThat(result.level()).isEqualTo(3);
            assertThat(result.title()).isEqualTo("高级会员");
            assertThat(result.minExp()).isEqualTo(1000);
            verify(levelConfigMapper).insert(any(LevelConfig.class));
        }

        @Test
        @DisplayName("should throw when level already exists")
        void createLevelConfig_levelExists_throwsException() {
            CreateLevelConfigRequest request = new CreateLevelConfigRequest(1, "重复等级", 0, null, null);
            when(levelConfigMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminGrowthService.createLevelConfig(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("LEVEL_ALREADY_EXISTS");
                    });

            verify(levelConfigMapper, never()).insert(any(LevelConfig.class));
        }
    }

    @Nested
    @DisplayName("updateLevelConfig")
    class UpdateLevelConfigTests {

        @Test
        @DisplayName("should update level config fields")
        void updateLevelConfig_success() {
            LevelConfig config = buildLevelConfig(1L, 1, "初级", 0);
            when(levelConfigMapper.selectById(1L)).thenReturn(config);

            UpdateLevelConfigRequest request = new UpdateLevelConfigRequest("新标题", 500, "new-icon.png", "新权益", 1);

            LevelConfigVO result = adminGrowthService.updateLevelConfig(1L, request);

            assertThat(result.title()).isEqualTo("新标题");
            assertThat(result.minExp()).isEqualTo(500);
            assertThat(result.icon()).isEqualTo("new-icon.png");
            verify(levelConfigMapper).updateById(config);
        }

        @Test
        @DisplayName("should only update non-null fields")
        void updateLevelConfig_partialUpdate() {
            LevelConfig config = buildLevelConfig(1L, 1, "初级", 0);
            when(levelConfigMapper.selectById(1L)).thenReturn(config);

            UpdateLevelConfigRequest request = new UpdateLevelConfigRequest("新标题", null, null, null, null);

            LevelConfigVO result = adminGrowthService.updateLevelConfig(1L, request);

            assertThat(result.title()).isEqualTo("新标题");
            assertThat(result.minExp()).isEqualTo(0);
            verify(levelConfigMapper).updateById(config);
        }

        @Test
        @DisplayName("should throw when config not found")
        void updateLevelConfig_notFound_throwsException() {
            when(levelConfigMapper.selectById(999L)).thenReturn(null);

            UpdateLevelConfigRequest request = new UpdateLevelConfigRequest("标题", null, null, null, null);

            assertThatThrownBy(() -> adminGrowthService.updateLevelConfig(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("LEVEL_CONFIG_NOT_FOUND");
                    });

            verify(levelConfigMapper, never()).updateById(any(LevelConfig.class));
        }
    }

    @Nested
    @DisplayName("deleteLevelConfig")
    class DeleteLevelConfigTests {

        @Test
        @DisplayName("should delete existing level config")
        void deleteLevelConfig_success() {
            LevelConfig config = buildLevelConfig(1L, 1, "初级", 0);
            when(levelConfigMapper.selectById(1L)).thenReturn(config);

            adminGrowthService.deleteLevelConfig(1L);

            verify(levelConfigMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("should throw when deleting non-existent config")
        void deleteLevelConfig_notFound_throwsException() {
            when(levelConfigMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminGrowthService.deleteLevelConfig(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("LEVEL_CONFIG_NOT_FOUND");
                    });

            verify(levelConfigMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("listLevelConfigs")
    class ListLevelConfigsTests {

        @Test
        @DisplayName("should return paginated level config VOs")
        void listLevelConfigs_success() {
            LevelConfig config1 = buildLevelConfig(1L, 1, "初级", 0);
            LevelConfig config2 = buildLevelConfig(2L, 2, "中级", 500);

            Page<LevelConfig> configPage = new Page<>(1, 10, 2);
            configPage.setRecords(List.of(config1, config2));
            when(levelConfigMapper.selectPage(any(Page.class), any())).thenReturn(configPage);

            Page<LevelConfigVO> result = adminGrowthService.listLevelConfigs(1, 10);

            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getRecords().get(0).level()).isEqualTo(1);
            assertThat(result.getRecords().get(1).level()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty page when no configs")
        void listLevelConfigs_empty() {
            Page<LevelConfig> emptyPage = new Page<>(1, 10, 0);
            emptyPage.setRecords(List.of());
            when(levelConfigMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

            Page<LevelConfigVO> result = adminGrowthService.listLevelConfigs(1, 10);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getTotalCheckIns")
    class GetTotalCheckInsTests {

        @Test
        @DisplayName("should return total check-in count")
        void getTotalCheckIns_success() {
            when(dailyCheckInMapper.selectCount(any())).thenReturn(1500L);

            long result = adminGrowthService.getTotalCheckIns();

            assertThat(result).isEqualTo(1500L);
        }
    }

    @Nested
    @DisplayName("getTodayCheckIns")
    class GetTodayCheckInsTests {

        @Test
        @DisplayName("should return today check-in count")
        void getTodayCheckIns_success() {
            when(dailyCheckInMapper.selectCount(any())).thenReturn(42L);

            long result = adminGrowthService.getTodayCheckIns();

            assertThat(result).isEqualTo(42L);
        }
    }
}

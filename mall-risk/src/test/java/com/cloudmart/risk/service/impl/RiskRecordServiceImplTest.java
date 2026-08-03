package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.risk.converter.RiskConverter;
import com.cloudmart.risk.dto.RiskRecordDTO;
import com.cloudmart.risk.entity.RiskRecord;
import com.cloudmart.risk.repository.RiskRecordMapper;
import com.cloudmart.risk.vo.RiskRecordVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRecordServiceImplTest {

    private RiskRecordMapper riskRecordMapper;
    private RiskConverter riskConverter;
    private RiskRecordServiceImpl riskRecordService;

    private static final Long RECORD_ID = 1L;
    private static final Long USER_ID = 1001L;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(RiskRecord.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.risk.repository");
            TableInfoHelper.initTableInfo(assistant, RiskRecord.class);
        }
    }

    @BeforeEach
    void setUp() {
        riskRecordMapper = mock(RiskRecordMapper.class);
        riskConverter = mock(RiskConverter.class);
        riskRecordService = new RiskRecordServiceImpl(riskRecordMapper, riskConverter);
    }

    @Nested
    @DisplayName("createRecord")
    class CreateRecordTests {

        @Test
        @DisplayName("should create record and return VO")
        void createRecord_success_returnsVo() {
            RiskRecordDTO dto = new RiskRecordDTO(null, USER_ID, "ORDER", "HIGH", "BLOCK", 1L, "频繁下单", null, null);
            RiskRecord entity = new RiskRecord();
            entity.setId(RECORD_ID);
            entity.setUserId(USER_ID);
            entity.setActionType("ORDER");
            entity.setRiskLevel("HIGH");

            RiskRecordVO vo = new RiskRecordVO(RECORD_ID, USER_ID, "ORDER", "HIGH", "频繁下单", LocalDateTime.now());

            when(riskConverter.toEntity(dto)).thenReturn(entity);
            when(riskConverter.toRiskRecordVO(entity)).thenReturn(vo);

            RiskRecordVO result = riskRecordService.createRecord(dto);

            assertThat(result.id()).isEqualTo(RECORD_ID);
            assertThat(result.userId()).isEqualTo(USER_ID);
            verify(riskRecordMapper).insert(any(RiskRecord.class));
        }
    }

    @Nested
    @DisplayName("listRecords")
    class ListRecordsTests {

        @Test
        @DisplayName("should filter by userId when provided")
        void listRecords_withUserId_filtersByUser() {
            RiskRecord record = new RiskRecord();
            record.setId(RECORD_ID);
            record.setUserId(USER_ID);

            Page<RiskRecord> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(record));

            RiskRecordVO vo = new RiskRecordVO(RECORD_ID, USER_ID, "ORDER", "HIGH", "频繁下单", LocalDateTime.now());

            when(riskRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(riskConverter.toRiskRecordVOList(page.getRecords())).thenReturn(List.of(vo));

            List<RiskRecordVO> results = riskRecordService.listRecords(USER_ID, 1, 20);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("should return all records when userId is null")
        void listRecords_withoutUserId_returnsAll() {
            RiskRecord record1 = new RiskRecord();
            record1.setUserId(1001L);
            RiskRecord record2 = new RiskRecord();
            record2.setUserId(1002L);

            Page<RiskRecord> page = new Page<>(1, 20, 2L);
            page.setRecords(List.of(record1, record2));

            RiskRecordVO vo1 = new RiskRecordVO(1L, 1001L, "ORDER", "HIGH", "原因1", LocalDateTime.now());
            RiskRecordVO vo2 = new RiskRecordVO(2L, 1002L, "PAYMENT", "MEDIUM", "原因2", LocalDateTime.now());

            when(riskRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(riskConverter.toRiskRecordVOList(page.getRecords())).thenReturn(List.of(vo1, vo2));

            List<RiskRecordVO> results = riskRecordService.listRecords(null, 1, 20);

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no records exist")
        void listRecords_empty_returnsEmptyList() {
            Page<RiskRecord> page = new Page<>(1, 20, 0L);
            page.setRecords(List.of());

            when(riskRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(riskConverter.toRiskRecordVOList(page.getRecords())).thenReturn(List.of());

            List<RiskRecordVO> results = riskRecordService.listRecords(USER_ID, 1, 20);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRecord")
    class GetRecordTests {

        @Test
        @DisplayName("should return record VO when found")
        void getRecord_found_returnsVo() {
            RiskRecord entity = new RiskRecord();
            entity.setId(RECORD_ID);
            entity.setUserId(USER_ID);

            RiskRecordVO vo = new RiskRecordVO(RECORD_ID, USER_ID, "ORDER", "HIGH", "频繁下单", LocalDateTime.now());

            when(riskRecordMapper.selectById(RECORD_ID)).thenReturn(entity);
            when(riskConverter.toRiskRecordVO(entity)).thenReturn(vo);

            RiskRecordVO result = riskRecordService.getRecord(RECORD_ID);

            assertThat(result.id()).isEqualTo(RECORD_ID);
        }

        @Test
        @DisplayName("should throw BusinessException when record not found")
        void getRecord_notFound_throwsBusinessException() {
            when(riskRecordMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> riskRecordService.getRecord(RECORD_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("风控记录不存在");
        }
    }
}

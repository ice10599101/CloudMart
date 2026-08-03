package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.entity.AdminOperLog;
import com.cloudmart.admin.repository.AdminOperLogMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminOperLogServiceImplTest {

    private AdminOperLogMapper adminOperLogMapper;
    private AdminOperLogServiceImpl adminOperLogService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminOperLog.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminOperLogMapper");
            TableInfoHelper.initTableInfo(assistant, AdminOperLog.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminOperLogMapper = mock(AdminOperLogMapper.class);
        adminOperLogService = new AdminOperLogServiceImpl(adminOperLogMapper);
    }

    private AdminOperLog buildOperLog(Long id) {
        AdminOperLog log = new AdminOperLog();
        log.setId(id);
        log.setTitle("Test Operation");
        log.setBusinessType(1);
        log.setMethod("com.cloudmart.admin.controller.TestController.test()");
        log.setRequestMethod("GET");
        log.setOperatorType(1);
        log.setOperUserId(1L);
        log.setOperName("admin");
        log.setDeptName("IT Dept");
        log.setOperUrl("/api/test");
        log.setOperIp("127.0.0.1");
        log.setOperLocation("Local");
        log.setOperParam("{}");
        log.setJsonResult("{}");
        log.setStatus(0);
        log.setErrorMsg(null);
        log.setCostTime(100L);
        return log;
    }

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("inserts operation log")
        void save_ShouldInsertLog() {
            AdminOperLog log = buildOperLog(null);
            when(adminOperLogMapper.insert(any(AdminOperLog.class))).thenReturn(1);

            adminOperLogService.save(log);

            verify(adminOperLogMapper).insert(any(AdminOperLog.class));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("log exists -> returns response via mapper lookup")
        void getById_Exists_ShouldReturnResponse() {
            AdminOperLog log = buildOperLog(1L);
            when(adminOperLogMapper.selectById(1L)).thenReturn(log);

            var response = adminOperLogService.getById(1L);

            assertThat(response).isNotNull();
            verify(adminOperLogMapper).selectById(1L);
        }

        @Test
        @DisplayName("log not found -> throws OPER_LOG_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminOperLogMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminOperLogService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("OPER_LOG_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("log exists -> deletes log")
        void delete_Exists_ShouldDelete() {
            AdminOperLog log = buildOperLog(1L);
            when(adminOperLogMapper.selectById(1L)).thenReturn(log);

            adminOperLogService.delete(1L);

            verify(adminOperLogMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("log not found -> throws OPER_LOG_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminOperLogMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminOperLogService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("OPER_LOG_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("clean")
    class CleanTests {

        @Test
        @DisplayName("deletes all operation logs")
        void clean_ShouldDeleteAll() {
            when(adminOperLogMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(10);

            adminOperLogService.clean();

            verify(adminOperLogMapper).delete(any(LambdaQueryWrapper.class));
        }
    }
}

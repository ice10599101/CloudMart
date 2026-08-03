package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.entity.AdminLoginLog;
import com.cloudmart.admin.repository.AdminLoginLogMapper;
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
class AdminLoginLogServiceImplTest {

    private AdminLoginLogMapper adminLoginLogMapper;
    private AdminLoginLogServiceImpl adminLoginLogService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminLoginLog.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminLoginLogMapper");
            TableInfoHelper.initTableInfo(assistant, AdminLoginLog.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminLoginLogMapper = mock(AdminLoginLogMapper.class);
        adminLoginLogService = new AdminLoginLogServiceImpl(adminLoginLogMapper);
    }

    private AdminLoginLog buildLoginLog(Long id) {
        AdminLoginLog log = new AdminLoginLog();
        log.setId(id);
        log.setUsername("admin");
        log.setIpaddr("127.0.0.1");
        log.setLoginLocation("Local");
        log.setBrowser("Chrome");
        log.setOs("Windows");
        log.setStatus(0);
        log.setMsg("Login successful");
        return log;
    }

    @Nested
    @DisplayName("recordLogin")
    class RecordLoginTests {

        @Test
        @DisplayName("inserts login log")
        void recordLogin_ShouldInsertLog() {
            when(adminLoginLogMapper.insert(any(AdminLoginLog.class))).thenReturn(1);

            adminLoginLogService.recordLogin("admin", "127.0.0.1", "Local", "Chrome", "Windows", 0, "Login successful");

            verify(adminLoginLogMapper).insert(any(AdminLoginLog.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("log exists -> deletes log")
        void delete_Exists_ShouldDelete() {
            AdminLoginLog log = buildLoginLog(1L);
            when(adminLoginLogMapper.selectById(1L)).thenReturn(log);

            adminLoginLogService.delete(1L);

            verify(adminLoginLogMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("log not found -> throws LOGIN_LOG_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminLoginLogMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminLoginLogService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("LOGIN_LOG_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("clean")
    class CleanTests {

        @Test
        @DisplayName("deletes all login logs")
        void clean_ShouldDeleteAll() {
            when(adminLoginLogMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(10);

            adminLoginLogService.clean();

            verify(adminLoginLogMapper).delete(any(LambdaQueryWrapper.class));
        }
    }
}

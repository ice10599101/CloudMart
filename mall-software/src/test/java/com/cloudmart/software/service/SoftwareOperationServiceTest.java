package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.repository.SoftwareOperationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 6.10 操作幂等单测（A09）：同键同请求返回原结果不重复执行；同键异体 409；
 * 并发首插取已提交结果；无事务上下文拒绝执行。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareOperationServiceTest {

    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    @Mock
    private SoftwareOperationMapper operationMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SoftwareOperationService service;

    @BeforeEach
    void setUp() {
        service = new SoftwareOperationService(operationMapper, objectMapper);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private com.cloudmart.software.entity.SoftwareOperation stored(String hash, String resultJson) {
        var record = new com.cloudmart.software.entity.SoftwareOperation();
        record.setId(1L);
        record.setActorType("USER");
        record.setActorId(42L);
        record.setOperationType(SoftwareOperationService.OP_CREATE_SOFTWARE);
        record.setOperationKey("key-1");
        record.setRequestHash(hash);
        record.setResultJson(resultJson);
        return record;
    }

    @Test
    @DisplayName("首次执行：运行业务动作并同事务写入结果记录（只存 ID/版本/状态）")
    void execute_firstRunInvokesAction() {
        when(operationMapper.selectOne(any())).thenReturn(null);

        var result = service.execute("USER", 42L, SoftwareOperationService.OP_CREATE_SOFTWARE,
                "key-1", Map.of("userId", 42L), null,
                () -> new SoftwareCreated(100L, 501L), json -> objectMapper.readValue(json, SoftwareCreated.class));

        assertThat(result.softwareId()).isEqualTo(100L);
        ArgumentCaptor<com.cloudmart.software.entity.SoftwareOperation> captor =
                ArgumentCaptor.forClass(com.cloudmart.software.entity.SoftwareOperation.class);
        verify(operationMapper, times(1)).insert((com.cloudmart.software.entity.SoftwareOperation) captor.capture());
        assertThat(captor.getValue().getResultJson()).contains("\"softwareId\":100");
    }

    @Test
    @DisplayName("同键同请求：返回存储结果，业务动作绝不重复执行（重试不重复创建）")
    void execute_sameRequestReplays() {
        String sameHash = service.requestHash(Map.of("userId", 42L));
        when(operationMapper.selectOne(any())).thenReturn(
                stored(sameHash, "{\"softwareId\":100,\"revisionId\":501}"));

        SoftwareCreated result = service.execute("USER", 42L, SoftwareOperationService.OP_CREATE_SOFTWARE,
                "key-1", Map.of("userId", 42L), null,
                () -> {
                    throw new IllegalStateException("不应重复执行创建逻辑");
                },
                json -> objectMapper.readValue(json, SoftwareCreated.class));

        assertThat(result.softwareId()).isEqualTo(100L);
        verify(operationMapper, never()).insert(any(com.cloudmart.software.entity.SoftwareOperation.class));
    }

    @Test
    @DisplayName("同键异体：409 SOFTWARE_OPERATION_CONFLICT")
    void execute_differentRequestConflicts() {
        when(operationMapper.selectOne(any())).thenReturn(stored(HASH, "{}"));

        assertThatThrownBy(() -> service.execute("USER", 42L, SoftwareOperationService.OP_CREATE_SOFTWARE,
                "key-1", Map.of("other", true), null, () -> null,
                json -> objectMapper.readValue(json, SoftwareCreated.class)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_OPERATION_CONFLICT");
    }

    @Test
    @DisplayName("并发首插冲突：对方已提交且同请求 → 返回其结果；未提交可见 → 冲突可重试")
    void execute_duplicateKeyRace() {
        String sameHash = service.requestHash(Map.of("userId", 42L));
        when(operationMapper.selectOne(any()))
                .thenReturn(null)                                 // 首查无记录
                .thenReturn(stored(sameHash, "{\"softwareId\":100,\"revisionId\":501}")); // 冲突后已提交可见
        when(operationMapper.insert(any(com.cloudmart.software.entity.SoftwareOperation.class))).thenThrow(new DuplicateKeyException("uk_operation_key"));

        SoftwareCreated result = service.execute("USER", 42L, SoftwareOperationService.OP_CREATE_SOFTWARE,
                "key-1", Map.of("userId", 42L), null,
                () -> new SoftwareCreated(999L, 999L),
                json -> objectMapper.readValue(json, SoftwareCreated.class));

        // 不重复执行业务：返回对方已提交的 100，而非本方 999
        assertThat(result.softwareId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("非法入参拒绝；无事务上下文拒绝执行（结果记录必须与业务同事务）")
    void execute_validation() {
        assertThatThrownBy(() -> service.execute("USER", 42L, OP, "bad key with space".repeat(20),
                Map.of(), null, () -> null, json -> null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_OPERATION_INVALID");

        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(() -> service.execute("USER", 42L, OP, "key-1", Map.of(), null,
                () -> null, json -> null))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final String OP = SoftwareOperationService.OP_CREATE_SOFTWARE;

    /** 测试用结果载体 */
    record SoftwareCreated(Long softwareId, Long revisionId) {
    }
}

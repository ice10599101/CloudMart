package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishOperation;
import com.cloudmart.wish.repository.WishOperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 持久幂等执行器（B04，任务书 §6.2「持久幂等的最低实现」）。
 *
 * <p>流程：事务内插入操作唯一键 → 键冲突时读已提交视图对比摘要：
 * 同键同摘要重放第一次已提交结果，同键异摘要 409 IDEMPOTENCY_KEY_REUSED →
 * 执行领域写（库存/钱包/事实）→ 写完成结果 → 一起提交。
 * 业务异常整体回滚（含操作行），不留下"已成功"凭证。</p>
 *
 * <p>不变量：</p>
 * <ul>
 *   <li>operationId 是持久业务凭证，重放不依赖 Redis TTL；</li>
 *   <li>数据库不可用即拒绝资源变更（fail closed）；</li>
 *   <li>重放绑定同一 actor 作用域，不跨端点/跨身份串用结果。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishOperationExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final WishOperationMapper operationMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 在持久幂等保护下执行业务。
     *
     * @param actorType     操作者类型（USER/ADMIN/JOB/SERVICE）
     * @param actorId       数值型操作者 ID（服务主体填 0）
     * @param actorRef      非数值主体标识（可空）
     * @param operationType 操作类型（如 ASSET_EXCHANGE）
     * @param requestKey    请求键（客户端幂等键；空白时按单次请求生成——无跨重试保护）
     * @param hashPayload   参与摘要的业务参数（同键不同内容必须产生不同摘要）
     * @param resultType    结果反序列化类型（重放时使用）
     * @param business      领域写操作（在当前事务内执行）
     */
    public <T> T execute(String actorType, Long actorId, String actorRef,
                         String operationType, String requestKey, Object hashPayload,
                         Class<T> resultType, Supplier<T> business) {
        String key = (requestKey == null || requestKey.isBlank())
                ? UUID.randomUUID().toString()
                : requestKey.trim();
        if (key.length() > 128) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "幂等键过长");
        }
        String hash = sha256(operationType + "|" + toJson(hashPayload));

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return executeInTx(actorType, actorId, actorRef, operationType, key, hash, resultType, business);
        }
        // 无调用方事务：自建事务保证"操作凭证+领域写"原子提交
        return transactionTemplate.execute(status ->
                executeInTx(actorType, actorId, actorRef, operationType, key, hash, resultType, business));
    }

    private <T> T executeInTx(String actorType, Long actorId, String actorRef,
                              String operationType, String key, String hash,
                              Class<T> resultType, Supplier<T> business) {
        boolean inserted;
        WishOperation row = newOperationRow(actorType, actorId, actorRef, operationType, key, hash);
        try {
            operationMapper.insert(row);
            inserted = true;
        } catch (DuplicateKeyException ex) {
            // 键冲突：不执行业务，读已提交视图决定重放或冲突
            inserted = false;
        }
        if (!inserted) {
            return replayOrConflict(actorType, actorId, operationType, key, hash, resultType);
        }

        T result = business.get();

        row.setStatus("COMPLETED");
        row.setResponseJson(toJson(result));
        row.setCompletedAt(LocalDateTime.now(ZoneId.of("UTC")));
        operationMapper.updateById(row);
        return result;
    }

    /** 键冲突：读已提交视图，按摘要决定重放或 409。 */
    private <T> T replayOrConflict(String actorType, Long actorId, String operationType,
                                   String key, String hash, Class<T> resultType) {
        WishOperation committed = operationMapper.selectOne(new LambdaQueryWrapper<WishOperation>()
                .eq(WishOperation::getActorType, actorType)
                .eq(WishOperation::getActorId, actorId)
                .eq(WishOperation::getOperationType, operationType)
                .eq(WishOperation::getRequestKey, key)
                .last("LIMIT 1"));
        if (committed == null) {
            // 原事务回滚竞态（行已消失）：结果未知，调用方按原键重试
            throw new BusinessException(WishErrorCodes.WISH_OPERATION_IN_PROGRESS,
                    "操作处理中，请按原键重试");
        }
        if (!hash.equals(committed.getRequestHash())) {
            throw new BusinessException(WishErrorCodes.IDEMPOTENCY_KEY_REUSED,
                    "幂等键已用于不同内容，请更换请求键");
        }
        T result;
        try {
            result = MAPPER.readValue(committed.getResponseJson(), resultType);
        } catch (Exception e) {
            log.error("幂等结果反序列化失败 type={} key={}", operationType, key, e);
            throw new BusinessException(WishErrorCodes.WISH_OPERATION_IN_PROGRESS,
                    "操作结果读取失败，请按原键重试");
        }
        log.info("幂等重放: type={} actor={}/{} key={}", operationType, actorType, actorId, key);
        return result;
    }

    private WishOperation newOperationRow(String actorType, Long actorId, String actorRef,
                                          String operationType, String key, String hash) {
        WishOperation row = new WishOperation();
        row.setActorType(actorType);
        row.setActorId(actorId);
        row.setActorRef(actorRef);
        row.setOperationType(operationType);
        row.setRequestKey(key);
        row.setRequestHash(hash);
        row.setStatus("PROCESSING");
        return row;
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("操作结果序列化失败", e);
        }
    }
}

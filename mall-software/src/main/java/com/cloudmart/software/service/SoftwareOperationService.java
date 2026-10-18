package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareOperation;
import com.cloudmart.software.repository.SoftwareOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 操作幂等事实服务（方案 6.10/7.3）：创建软件/克隆工作快照/发布提交/审核切换/
 * 创建评论等使用。业务变更与结果记录同事务提交（本服务须在调用方事务内执行）。
 *
 * <p>语义：同键同 request_hash → 返回首次结果（不重复执行业务）；
 * 同键不同 request_hash → 409；首次执行先写唯一占位再执行业务（SW05），
 * 冲突方在未产生任何业务写入前就失败，随后用当前读取回赢家已提交结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareOperationService {

    /** 操作类型常量：必须区分业务，避免同 key 意外撞用（7.3） */
    public static final String OP_CREATE_SOFTWARE = "CREATE_SOFTWARE";
    public static final String OP_CLONE_WORKING = "CLONE_WORKING";
    public static final String OP_SUBMIT = "SUBMIT_REVISION";
    public static final String OP_REVIEW_DECISION = "REVIEW_DECISION";
    public static final String OP_CREATE_COMMENT = "CREATE_COMMENT";
    public static final String OP_CREATE_REPORT = "CREATE_REPORT";
    public static final String OP_PERMISSION_TOGGLE = "PUBLISHER_PERMISSION_TOGGLE";
    public static final String OP_ACCESS_GRANT = "ACCESS_GRANT";
    public static final String OP_ACCESS_REVOKE = "ACCESS_GRANT_REVOKE";
    public static final String OP_CONTACT_SAVE = "ACCESS_CONTACT_SAVE";
    public static final String OP_REPORT_HANDLE = "REPORT_HANDLE";
    public static final String OP_FEEDBACK_MARK = "LINK_FEEDBACK_MARK";
    /** SF03：发布者申请提交/取消（幂等主体=申请人） */
    public static final String OP_PUBLISHER_APPLY = "PUBLISHER_APPLY";
    public static final String OP_PUBLISHER_CANCEL = "PUBLISHER_APPLICATION_CANCEL";
    /** SF04：受限来源访问申请提交/取消（幂等主体=申请人+软件） */
    public static final String OP_ACCESS_REQUEST = "ACCESS_REQUEST_SUBMIT";
    public static final String OP_ACCESS_REQUEST_CANCEL = "ACCESS_REQUEST_CANCEL";
    public static final String OP_COMMENT_MODERATE = "COMMENT_MODERATE";
    public static final String OP_ITEM_BAN = "SOFTWARE_BAN";
    public static final String OP_ITEM_UNBAN = "SOFTWARE_UNBAN";
    public static final String OP_ITEM_FEATURE = "SOFTWARE_FEATURE";
    public static final String OP_CATEGORY_SAVE = "CATEGORY_SAVE";
    public static final String OP_CATEGORY_DISABLE = "CATEGORY_DISABLE";
    public static final String OP_TAG_SAVE = "TAG_SAVE";
    public static final String OP_TAG_DISABLE = "TAG_DISABLE";
    public static final String OP_TOPIC_SAVE = "TOPIC_SAVE";
    public static final String OP_TOPIC_DISABLE = "TOPIC_DISABLE";

    private final SoftwareOperationMapper operationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 在调用方事务内执行幂等操作。
     *
     * @param actorType    USER/ADMIN（区分 ID 命名空间）
     * @param actorId      操作者
     * @param operationType 业务操作类型（见常量）
     * @param operationKey 客户端 Idempotency-Key（1~128 字符）
     * @param request      请求体（稳定序列化后取 SHA-256 作为 requestHash）
     * @param softwareId   关联软件（可空操作传 null）
     * @param action       首次执行的业务动作（结果须可 JSON 序列化且只含 ID/版本/状态）
     * @param resultReader 重放路径：把存储的结果 JSON 反序列化为调用方期望类型
     * @return 首次执行或历史重放结果
     */
    public <T> T execute(String actorType, Long actorId, String operationType,
                         String operationKey, Object request, Long softwareId,
                         Supplier<T> action, Function<String, T> resultReader) {
        String requestHash = requestHash(request);
        validate(actorType, actorId, operationKey, requestHash);
        SoftwareOperation existing = find(actorType, actorId, operationType, operationKey);
        if (existing != null) {
            return replayOrConflict(existing, requestHash, resultReader);
        }

        // SW05：先插入唯一占位再执行业务（唯一键就是并发闸门）。
        // 占位与业务同事务：业务失败/进程崩溃时占位一起回滚，不会残留“处理中”死记录；
        // 其他事务在本事务提交前看不到占位行，因此不会读到未完成结果。
        SoftwareOperation record = new SoftwareOperation();
        record.setActorType(actorType);
        record.setActorId(actorId);
        record.setOperationKey(operationKey);
        record.setOperationType(operationType);
        record.setRequestHash(requestHash);
        record.setSoftwareId(softwareId);
        record.setResultJson("{}");
        try {
            operationMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突只会在对方事务提交后报出；本事务未执行任何业务写入。
            // 本事务快照可能看不到对方新提交的行，必须用当前读（FOR UPDATE）取已提交结果。
            SoftwareOperation committed = findForUpdate(actorType, actorId, operationType, operationKey);
            if (committed == null) {
                throw new BusinessException("SOFTWARE_OPERATION_CONFLICT",
                        "相同幂等键的操作正在处理中，请稍后重试");
            }
            return replayOrConflict(committed, requestHash, resultReader);
        }

        T result = action.get();
        record.setResultJson(toResultJson(result));
        if (operationMapper.updateById(record) != 1) {
            throw new BusinessException("SOFTWARE_OPERATION_CONFLICT", "幂等结果写入失败，请重试");
        }
        return result;
    }

    /**
     * 副作用前的重放检查（SW04）：同键同请求直接返回历史结果，异体 409；
     * 未命中返回 empty。调用方须先完成身份/归属校验。
     */
    public <T> java.util.Optional<T> replay(String actorType, Long actorId,
                                            String operationType, String operationKey,
                                            Object request, Function<String, T> resultReader) {
        String requestHash = requestHash(request);
        validate(actorType, actorId, operationKey, requestHash);
        SoftwareOperation existing = find(actorType, actorId, operationType, operationKey);
        if (existing == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(replayOrConflict(existing, requestHash, resultReader));
    }

    /** 同键重放：同请求返回原结果；异体 409（绝不重复执行业务动作） */
    private <T> T replayOrConflict(SoftwareOperation existing, String requestHash,
                                   Function<String, T> resultReader) {
        if (existing.getRequestHash().equals(requestHash)) {
            // 结果只存 ID/版本/状态 JSON（6.10），按调用方声明的类型重建
            return resultReader.apply(existing.getResultJson());
        }
        throw new BusinessException("SOFTWARE_OPERATION_CONFLICT",
                "该 Idempotency-Key 已被不同请求使用，请更换后重试");
    }

    private SoftwareOperation find(String actorType, Long actorId, String operationType,
                                   String operationKey) {
        return operationMapper.selectOne(new LambdaQueryWrapper<SoftwareOperation>()
                .eq(SoftwareOperation::getActorType, actorType)
                .eq(SoftwareOperation::getActorId, actorId)
                .eq(SoftwareOperation::getOperationType, operationType)
                .eq(SoftwareOperation::getOperationKey, operationKey));
    }

    /**
     * 当前读（SW05）：唯一键冲突后必须绕过本事务快照读取对方已提交结果，
     * 否则 REPEATABLE READ 下可能看不到刚提交的赢家行。
     */
    private SoftwareOperation findForUpdate(String actorType, Long actorId, String operationType,
                                            String operationKey) {
        return operationMapper.selectOne(new LambdaQueryWrapper<SoftwareOperation>()
                .eq(SoftwareOperation::getActorType, actorType)
                .eq(SoftwareOperation::getActorId, actorId)
                .eq(SoftwareOperation::getOperationType, operationType)
                .eq(SoftwareOperation::getOperationKey, operationKey)
                .last("FOR UPDATE"));
    }

    private void validate(String actorType, Long actorId, String operationKey, String requestHash) {
        if (!"USER".equals(actorType) && !"ADMIN".equals(actorType)) {
            throw new BusinessException("SOFTWARE_OPERATION_INVALID", "actorType 非法");
        }
        if (actorId == null || actorId <= 0) {
            throw new BusinessException("SOFTWARE_OPERATION_INVALID", "actorId 非法");
        }
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new BusinessException("SOFTWARE_OPERATION_INVALID",
                    "Idempotency-Key 须为 1~128 字符");
        }
        if (!requestHash.matches("[0-9a-f]{64}")) {
            throw new BusinessException("SOFTWARE_OPERATION_INVALID", "requestHash 须为 SHA-256 hex");
        }
        // 结果记录与业务变更同事务：无事务上下文即为编程错误（6.10）
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("幂等操作必须在事务内执行（业务变更与结果记录同事务提交）");
        }
    }

    private String toResultJson(Object result) {
        try {
            return result instanceof String text ? text : objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("幂等结果序列化失败（结果须为 ID/版本/状态）", e);
        }
    }

    /** 请求体规范化摘要：稳定序列化后取 SHA-256（同键异体 409 判定） */
    public String requestHash(Object request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        } catch (Exception e) {
            throw new BusinessException("SOFTWARE_OPERATION_INVALID", "请求体不可序列化");
        }
    }
}

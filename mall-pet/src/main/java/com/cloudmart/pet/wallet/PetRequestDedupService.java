package com.cloudmart.pet.wallet;

/**
 * 请求级幂等去重服务（W01/§5.2 pet_request_dedup）。
 *
 * <p>两层幂等键的"请求键"层：{@code (userId, endpointKey, requestKey)}——网络重试、
 * 页面刷新、App 重启复用同一请求键收敛到同一结果；规范 payload 摘要不一致返回
 * {@code PET_IDEMPOTENCY_CONFLICT}（409，禁止自动换键，§8.6）。</p>
 *
 * <p>终态语义（§8.2）：业务成功与业务拒绝都保存终态响应（同键返回原结果）；
 * 仅崩溃/未知失败置 FAILED，同键可安全重试。</p>
 */
public interface PetRequestDedupService {

    /**
     * 占住请求键（独立小事务提交，调用方业务事务回滚不连带抹掉）。
     *
     * @return NEW=本次首次执行；EXISTING=已有终态结果（duplicate）；IN_PROGRESS=同键处理中
     */
    ClaimResult claim(Long userId, String endpointKey, String requestKey, String payloadHash);

    /**
     * P02：接管租约到期的 PROCESSING 行（CAS：仅当仍是 PROCESSING 且租约已到期时轮换租约、
     * 推进版本）。供同键重试与恢复扫描器调用；胜者按 NEW 重新执行，败者返回 false。
     */
    boolean tryTakeover(Long userId, String endpointKey, String requestKey);

    /**
     * 业务成功后写入终态响应（P02：REQUIRED——购买成功路径在业务事务内调用，
     * 与订单/钱包/资产同一事务提交；业务拒绝路径无事务时自成小事务）。
     *
     * @param expectedLeaseOwner R02 fencing：仅当行租约仍归属该执行者才允许写终态，
     *                           旧执行者（租约已被接管轮换）不得覆盖新执行者的终态；
     *                           传 null 表示无租约上下文（不经 claim 的锚点写入，保持原语义）
     */
    void completeSucceeded(Long userId, String endpointKey, String requestKey,
                           String expectedLeaseOwner, Long bizOrderId, String responseJson);

    /**
     * 未知失败（P02：REQUIRED；业务事务已回滚后调用，自成小事务）；
     * 同键下次重试重新执行。fencing 语义同 {@link #completeSucceeded}。
     */
    void markFailed(Long userId, String endpointKey, String requestKey,
                    String expectedLeaseOwner, String errorJson);

    /**
     * R02：旧请求无显式 petId 时冻结首次绑定归属（幂等：仅在 bound_pet_id 为空时写入）。
     * 重放/接管后由 claim 返回 boundPetId，同一意图不随主宠切换改投另一只宠物。
     */
    void bindPet(Long userId, String endpointKey, String requestKey, Long petId);

    /** 规范请求摘要：按固定次序序列化后 SHA-256（客户端 requestKey 不进入业务事实摘要） */
    String canonicalHash(Object... parts);

    /** @return requestKey 是否满足 16..128 ASCII 契约 */
    static boolean isValidRequestKey(String requestKey) {
        if (requestKey == null || requestKey.length() < 16 || requestKey.length() > 128) {
            return false;
        }
        for (int i = 0; i < requestKey.length(); i++) {
            if (requestKey.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    /** 占键结果（R02：携带租约归属与既有绑定宠物，供 fencing 终态回写与原归属重放） */
    record ClaimResult(Outcome outcome, Long dedupId, String responseJson,
                       String leaseOwner, Long boundPetId) {
        public enum Outcome { NEW, EXISTING, IN_PROGRESS }
    }
}

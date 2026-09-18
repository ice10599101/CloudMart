package com.cloudmart.file.service;

/**
 * 每日上传配额服务。
 *
 * <p>站点级规则：每用户每天最多成功上传 30 张图片，除图片外其他所有类型合计
 * 每天最多成功上传 15 次；管理员（网关注入 X-Admin-Role: admin）不受限。</p>
 *
 * <p>采用「先预占、失败退还」模型保证并发下的严格上限：
 * {@link #reserve} 原子自增并校验额度，文件落盘失败时调用 {@link #refund} 退还，
 * 使计数始终等于"成功上传次数"。</p>
 */
public interface UploadQuotaService {

    /**
     * 校验并预占一次上传额度。
     *
     * @param userId   用户 ID（网关注入的 X-User-Id）
     * @param filename 原始文件名，用于区分图片/非图片配额桶
     * @return true=已实际预占一个额度（后续失败需调用 {@link #refund} 退还）；
     *         false=未预占（配额开关关闭或 Redis 故障 Fail-Open 放行，无需退还）
     * @throws com.cloudmart.common.exception.BusinessException code=UPLOAD_DAILY_LIMIT_EXCEEDED 时表示当日已达上限
     */
    boolean reserve(String userId, String filename);

    /**
     * 退还一次预占额度（上传失败时调用）。
     *
     * <p>仅在 {@link #reserve} 返回 true 后调用；计数下限为 0，不会出现负数超额放行。</p>
     */
    void refund(String userId, String filename);
}

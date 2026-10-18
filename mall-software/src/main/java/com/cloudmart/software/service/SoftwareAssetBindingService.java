package com.cloudmart.software.service;

import java.util.List;

/**
 * 素材引用绑定装配（方案 8.2/9.7.3）：跨服务引用不假装同库事务——
 * 绑定在软件库事务外幂等执行；本地回滚后经补偿任务幂等解绑；
 * submit 前自愈重绑（无合法归属/绑定不能进入待审）。
 */
public interface SoftwareAssetBindingService {

    /**
     * 幂等绑定资产到快照（bizType=SOFTWARE_REVISION，bizId=revisionId）。
     * 事务外调用；任一失败抛 FILE_SERVICE_UNAVAILABLE（草稿保留，重试修复）。
     *
     * @param ownerUserId 资产归属人（文件服务校验 ownerUserId 一致）
     */
    void bindRevisionAssets(Long revisionId, Long ownerUserId, List<Long> assetIds);

    /**
     * SW08：登记"按操作归属"的解绑意图（先于本地事务持久化，崩溃可恢复）。
     *
     * @param operationId 保存/局部保存操作标识（每次尝试唯一）
     * @param kind        rollback=本地回滚后解绑本次新增；release=提交后释放被移除旧资产。
     *                    执行器解绑前会重新核对当前引用集合，过期意图自然跳过。
     */
    void registerUnbindCompensation(String operationId, Long revisionId,
                                    List<Long> assetIds, String kind);

    /**
     * SW08：撤销仍为 PENDING 的解绑意图（加入调用方事务一起提交；
     * 保存成功后撤销 rollback 意图，避免无谓解绑）。
     */
    void cancelUnbindCompensation(String operationId, Long revisionId,
                                  List<Long> assetIds, String kind);

    /**
     * 登记重绑补偿任务（克隆/保存后绑定失败自愈；task_id 幂等）。
     */
    void registerBindCompensation(Long revisionId, Long ownerUserId, List<Long> assetIds);
}

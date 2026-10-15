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
     * 登记解绑补偿任务（本地事务回滚后调用；task_id 幂等）。
     */
    void registerUnbindCompensation(Long revisionId, List<Long> assetIds);

    /**
     * 登记重绑补偿任务（克隆/保存后绑定失败自愈；task_id 幂等）。
     */
    void registerBindCompensation(Long revisionId, Long ownerUserId, List<Long> assetIds);
}

package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AdminEnvConfigRequest;
import com.cloudmart.wish.dto.TriggerSpecialEventRequest;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;

import java.util.List;

/**
 * 管理端生命树环境服务（文档 Sprint 2.2 管理后台：特殊事件触发台 +
 * 环境配置管理表化）。
 *
 * <p>由 mall-admin 经 Feign 代理转发（hasRole('INTERNAL')），
 * 权限点 {@code business:treeEnv:*} 在管理后台配置。</p>
 */
public interface AdminTreeEnvService {

    /**
     * 触发全站特殊事件（如流星雨；文档 Sprint 2.2 特殊事件触发台）。
     *
     * <p>单活跃事件语义：自动结束当前活跃事件后插入新事件（同一事务）。
     * eventCode 须为 wish_env_config 中已启用环境（渲染配置存在）；
     * title/description 为空时取环境配置的名称/描述。</p>
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         TREE_ENV_CONFIG_NOT_FOUND（eventCode 无启用配置）
     */
    SpecialEventVO triggerSpecialEvent(TriggerSpecialEventRequest request, Long adminUserId);

    /**
     * 手动结束特殊事件（幂等：已结束直接返回）。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         TREE_SPECIAL_EVENT_NOT_FOUND
     */
    SpecialEventVO endSpecialEvent(Long eventId);

    /**
     * 特殊事件列表（管理端触发台历史记录，triggeredAt 倒序）。
     *
     * @param limit 返回条数上限（1-200）
     */
    List<SpecialEventVO> listSpecialEvents(int limit);

    /**
     * 环境配置全量列表（含下架，管理端配置管理）。
     *
     * @return 按 priority 降序（同 priority 按 envCode 升序稳定排序）
     */
    List<EnvConfigVO> listEnvConfigs();

    /**
     * 新增环境配置（表化：新增"中秋"等环境仅插行不改代码）。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         TREE_ENV_CONFIG_CODE_DUPLICATED（code 唯一冲突，含并发兜底）
     *         / TREE_ENV_VISUAL_INVALID（visual 非法 JSON 对象）
     */
    EnvConfigVO createEnvConfig(AdminEnvConfigRequest request);

    /**
     * 编辑环境配置（envCode 不可修改——code 是天气/季节/事件链路关联键）。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         TREE_ENV_CONFIG_NOT_FOUND / TREE_ENV_VISUAL_INVALID
     *         / WISH_VALIDATION_ERROR（尝试修改 envCode）
     */
    EnvConfigVO updateEnvConfig(Long configId, AdminEnvConfigRequest request);

    /**
     * 上/下架环境配置。
     *
     * <p>下架语义：不出现于公开配置列表；特殊事件触发校验失败（无启用
     * 配置）；进行中的关联事件不受影响（事件已固化视觉快照语义由前端
     * 按事件 code 取配置，下架后事件结束前配置缺失由前端降级渲染）。</p>
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         TREE_ENV_CONFIG_NOT_FOUND
     */
    EnvConfigVO updateEnvConfigStatus(Long configId, boolean active);
}

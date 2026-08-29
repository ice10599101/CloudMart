package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.LiveWidgetConfig;
import com.cloudmart.wish.vo.LiveWidgetVO;

import java.util.List;

/**
 * 直播心愿挂件服务（Sprint 3.4，文档 3.4）。
 *
 * <p>数据源与 live 模块解耦：挂件数据全部来自 wish 域（心愿进度/
 * 打卡天数/星光），前端在直播间叠加；wish 服务不可用时前端降级隐藏
 * 挂件、直播正常（降级策略）。</p>
 */
public interface LiveWidgetService {

    /**
     * 挂件数据（公开；Redis 缓存 TTL 10s——验收：主播打卡/点亮后
     * 10s 内更新 + 1000 观众轮询服务端无压力）。
     *
     * <p>主播无进行中心愿 → hasWish=false（前端展示"去许愿"引导）；
     * 全局降级开关（灰度 feature wish_live_widget 比例 0）或主播配置
     * is_visible=false → visible=false（前端隐藏挂件）。</p>
     */
    LiveWidgetVO getWidgetData(Long streamerId);

    /** 配置列表（管理端，全量） */
    List<LiveWidgetConfig> listConfigs();

    /** 保存配置（streamer 维度 upsert；position/style 校验） */
    LiveWidgetConfig saveConfig(LiveWidgetConfig config);

    /** 启用/停用某主播挂件 */
    void toggleConfig(Long streamerId, boolean visible);
}

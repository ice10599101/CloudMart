package com.cloudmart.wish.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LBS 地图配置（Sprint 3.1，wish.map.*，Nacos 可热更）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wish.map")
public class WishMapProperties {

    /** 默认城市中心纬度（空坐标兜底：客户端传 0,0 或 null） */
    private double defaultLat = 23.1291;

    /** 默认城市中心经度 */
    private double defaultLng = 113.2644;

    /** 附近查询缓存基础 TTL（秒，随机抖动 0-60s 追加防击穿） */
    private int cacheTtlSeconds = 300;

    /** 单次附近查询返回上限 */
    private int maxResults = 200;

    /** 交通枢纽 geohash4 白名单（伪造检测放宽：枢纽网格内跳跃不标记） */
    private java.util.List<String> hubGeohash4 = new java.util.ArrayList<>();
}

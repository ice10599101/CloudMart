package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.LbsFreeze;
import com.cloudmart.wish.entity.LbsSuspicious;
import com.cloudmart.wish.vo.EncounterLetterVO;

import java.util.List;

/**
 * 擦肩而过服务（Sprint 3.3，文档 2.10/十三/3.3/39.9）。
 *
 * <p>轨迹存储采用 Redis（文档 39.x 首选：TTL 25h 自动过期替代 MySQL
 * 逐行 DELETE；key lbs:trace:{bucket}:{geohash6}，仅存 geohash6+标签，
 * 无原始坐标）。</p>
 */
public interface EncounterService {

    /** 附近模式开关（开启=允许轨迹上报 24h 内有效，上报自动续期；关闭=立即删除） */
    void setNearbyMode(Long userId, boolean enabled);

    /** 附近模式当前状态（Redis 开关键存在即开启；供前端刷新后回显） */
    boolean isNearbyModeEnabled(Long userId);

    /**
     * 轨迹上报（附近模式开启后客户端每 5 分钟调用）：
     * 频率限制（5 分钟 >10 次 → 429）→ 冻结检查（403）→ 伪造检测
     * （速度 >15km/h 标记可疑，交通枢纽放宽，连续 3 次 → 冻结 24h）→
     * geohash6 + 30 分钟桶入库（Redis，原始坐标丢弃）。
     */
    void reportTrace(Long userId, Double lat, Double lng);

    /**
     * 匹配 + 投递（mall-job 每 30 分钟）：相同 geohash6 + 标签交集 +
     * 相邻桶 + 匿名人群 k≥5 → 生成镜像信笺（deliver_after 随机 1-24h）；
     * 到期 PENDING → DELIVERED + 匿名通知。
     *
     * @return 统计 {scannedCells, generatedLetters, deliveredLetters}
     */
    MatchStats matchAndDeliver();

    /**
     * 轨迹补偿清理（mall-job 每小时；Redis TTL 为主，此任务兜底删除
     * 超 24h 的桶 key 并返回统计）。
     */
    CleanupStats cleanupTraces();

    /** 信笺列表（PENDING 时 content 返回 null——契约） */
    List<EncounterLetterVO> listLetters(Long userId);

    /** 拆信（DELIVERED → READ，记录 read_at） */
    /** 信笺互动列表（文档 2.19：仅信笺归属者可见，匿名化不含互动者 ID） */
    java.util.List<com.cloudmart.wish.entity.LetterInteraction> listLetterInteractions(Long userId, Long letterId);

    EncounterLetterVO markRead(Long userId, Long letterId);

    /**
     * 信笺匿名互动（单信笺每日 1 次）：BLESS 免费 / LIGHT 扣星光 2 +
     * 对方心愿 support_count+1；对方收到匿名通知（不含 letterId/userId）。
     */
    EncounterLetterVO interact(Long userId, Long letterId, String type, String content);

    /**
     * 匹配/投递统计。
     */
    record MatchStats(int scannedCells, int generatedLetters, int deliveredLetters) {
    }

    /**
     * 清理统计。
     */
    record CleanupStats(int liveKeys, int deletedKeys) {
    }

    // ---------------- 管理端（位置伪造检测面板） ----------------

    /** 可疑跳跃记录（24h 内，id 倒序） */
    List<LbsSuspicious> listSuspicious(Long userId);

    /** 冻结用户列表（未解冻） */
    List<LbsFreeze> listFreezes();

    /** 解冻（删除冻结记录） */
    void unfreeze(Long userId);
}

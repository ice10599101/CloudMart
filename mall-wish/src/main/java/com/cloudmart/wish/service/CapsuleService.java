package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateCapsuleRequest;
import com.cloudmart.wish.entity.TimeCapsule;
import com.cloudmart.wish.vo.CapsuleVO;

import java.util.Map;

/**
 * 时间胶囊服务接口（文档 2.7，Sprint 2.4）。
 */
public interface CapsuleService {

    /**
     * 创建胶囊（status=SEALED）。
     *
     * <p>校验：openAt 必须晚于当前时间（WISH_OPEN_AT_PAST）且不早于/晚于
     * 自定义边界（最小 1 天、最大 10 年，WISH_VALIDATION_ERROR）；openAtTz
     * 须为合法 IANA 时区。内容 XSS 转义后封存。</p>
     */
    CapsuleVO createCapsule(Long userId, CreateCapsuleRequest request);

    /**
     * 我的胶囊列表（id 倒序游标分页）。
     *
     * @param status   状态过滤（可空 = 全部）
     * @param cursor   游标（首页不传）
     * @param pageSize 页大小（默认 20，上限 50）
     */
    CapsulePage listMyCapsules(Long userId, String status, String cursor, Integer pageSize);

    /**
     * 胶囊详情（仅本人可见；非本人统一 404 防存在性探测）。
     * 非 OPENED 状态 content/mediaUrls 返回 null。
     */
    CapsuleVO getCapsuleDetail(Long userId, Long capsuleId);

    /**
     * 到期开启（状态机 CAS：SEALED/AVAILABLE 且 openAt ≤ now → OPENED）。
     *
     * <p>未到期抛 WISH_CAPSULE_NOT_AVAILABLE(409)；已开启幂等返回内容
     * （并发双开仅一次生效）。</p>
     */
    CapsuleVO openCapsule(Long userId, Long capsuleId);

    /**
     * 取消胶囊（SEALED/AVAILABLE → CANCELLED，封存内容永久不可开启）。
     */
    CapsuleVO cancelCapsule(Long userId, Long capsuleId);

    /**
     * 扫描到期胶囊（mall-job 每 10 分钟触发，文档 9.2）。
     *
     * <p>分批（500 条/批）CAS 流转 SEALED→AVAILABLE，仅对流转成功的
     * 胶囊发布到期待开启通知（幂等：重复扫描不重复推送）。跨时区语义：
     * 仅比较 UTC openAt（文档 26.3）。</p>
     *
     * @return scanned 本轮扫描总数 / available 流转为待开启数
     */
    ScanResult scanAvailableCapsules();

    /**
     * 上报时区（文档 2.15）：写入 wish_user_stat.timezone，幂等。
     * 校验 IANA 格式；offsetMinutes 仅校验不存储。
     */
    Map<String, Object> reportTimezone(Long userId, String timezone, Integer offsetMinutes);

    /**
     * 管理端统计（创建数/开启数/到期数等）。
     */
    Map<String, Object> getAdminStats();

    /** 游标分页结果。 */
    record CapsulePage(java.util.List<CapsuleVO> records, String nextCursor, boolean hasMore) {
    }

    /** 扫描结果。 */
    record ScanResult(int scanned, int available) {
    }

    /** 内部流转载体（扫描事务外发通知用）。 */
    record AvailableCapsule(TimeCapsule capsule) {
    }
}

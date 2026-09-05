package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.ResourceLogType;
import com.cloudmart.wish.vo.LevelUpVO;
import com.cloudmart.wish.vo.MyLevelVO;
import com.cloudmart.wish.vo.MyResourcesVO;
import com.cloudmart.wish.vo.ResourceLogVO;

import java.util.List;

/**
 * 用户心愿统计服务接口。
 *
 * <p>维护 {@code wish_user_stat} 表的聚合数据，避免实时 count。
 * 所有方法必须在调用方的事务上下文中执行（同事务保证一致性）。</p>
 *
 * <p>统计规则（对应文档 6.5 等级机制）：</p>
 * <ul>
 *   <li>{@code totalWishes}：累计创建心愿数（含已软删，只增不减）</li>
 *   <li>{@code activeWishes}：当前有效心愿数（创建 +1，软删 -1）</li>
 *   <li>{@code totalFulfilled}：累计还愿数（历史事实，不回退）</li>
 * </ul>
 *
 * <p>星光规则（文档 6.1/6.2/6.3/6.4 节）：</p>
 * <ul>
 *   <li>流水与余额更新必须同事务（6.4）</li>
 *   <li>余额上限 5000，超出不再累加（6.3）</li>
 *   <li>扣减不足抛 WISH_STARLIGHT_INSUFFICIENT（402）</li>
 * </ul>
 */
public interface UserStatService {

    /**
     * 初始化用户统计记录（用户首次创建心愿时调用）。
     * 若记录已存在则跳过（幂等）。
     *
     * @param userId 用户 ID
     */
    void initUserStat(Long userId);

    /**
     * 心愿创建时：totalWishes + 1，activeWishes + 1，更新 lastActiveAt。
     * 必须与 wish insert 同事务。
     *
     * @param userId 用户 ID
     */
    void incrementOnWishCreated(Long userId);

    /**
     * 心愿软删时：activeWishes - 1（totalWishes 不变，累计值永不回退）。
     * 必须与 wish 软删同事务。
     *
     * @param userId 用户 ID
     */
    void decrementOnWishDeleted(Long userId);

    /**
     * 还愿提交时：totalFulfilled + 1（历史事实，驳回也不回退，文档 6.5），
     * activeWishes - 1（FULFILLED 不再属于"进行中"，文档 6.5 字段用途说明；
     * 更新时机文档仅记载创建/软删两点，此处按字段语义补充还愿 -1），
     * 同事务判定徽章（FIRST_FULFILL 等）。
     *
     * @param userId 用户 ID
     * @return 本次新获得的徽章列表（未新获得为空列表）
     */
    List<WishBadge> incrementOnFulfilled(Long userId);

    /**
     * 原子扣减星光（文档 6.2 消耗）。
     *
     * <p>条件 UPDATE（balance >= cost）保证并发安全；扣减与流水写入同事务。
     * 余额不足时抛 {@code WISH_STARLIGHT_INSUFFICIENT}（HTTP 402）。</p>
     *
     * @param userId 用户 ID
     * @param cost   扣减数量（正整数）
     * @param source 业务来源
     * @param refId  关联业务 ID（互动/打卡记录 ID，可空）
     * @return 扣减后余额
     */
    int spendStarlight(Long userId, int cost, ResourceLogSource source, Long refId);

    /**
     * 发放星光（文档 6.1 获取）。
     *
     * <p>余额上限 5000（LEAST 截断，超出部分不再累加，文档 6.3 防囤积）；
     * 发放与流水写入同事务。流水 delta 记录实际入账量（截断后）。</p>
     *
     * @param userId 用户 ID
     * @param amount 发放数量（正整数）
     * @param source 业务来源
     * @param refId  关联业务 ID（可空）
     * @return 实际入账量（可能因上限截断小于 amount）
     */
    int earnStarlight(Long userId, int amount, ResourceLogSource source, Long refId);

    /**
     * 查询星光余额（不存在记录返回 0，只读不产生流水）。
     *
     * @param userId 用户 ID
     * @return 当前余额
     */
    int getStarlightBalance(Long userId);

    /**
     * 查询用户时区（限频 TTL 按用户时区计算当日 23:59:59，文档第 32 章）。
     * 不存在记录返回默认 Asia/Shanghai。
     *
     * @param userId 用户 ID
     * @return IANA 时区 ID
     */
    String getUserTimezone(Long userId);

    /**
     * 累计帮助他人次数 +1（文档 6.5：点亮/匿名星光时异步累加，MQ 消费者调用）。
     * 独立事务（无调用方事务上下文）。
     *
     * @param userId 点亮者用户 ID
     */
    void incrementTotalHelped(Long userId);

    /**
     * 个人星光概览（文档 L848：GET /wish/my/resources）。
     * 只读：余额取 stat 快照，今日收支按流水聚合（当日零点边界，
     * 与 createdAt 的 MetaObjectHandler 填充时区一致，自洽不跨时区错位）。
     *
     * @param userId 用户 ID
     * @return 余额 + 今日已获取/已消耗
     */
    MyResourcesVO getMyResources(Long userId);

    /**
     * 星光流水分页（文档 L848：GET /wish/my/resources/logs）。
     * 只读：按 id DESC（雪花 ID 恒等时序倒序），游标为上一页末条 id。
     *
     * @param userId   用户 ID
     * @param type     类型过滤（EARN / SPEND，null = 全部）
     * @param cursor   游标（上一页末条流水 ID，null = 第一页）
     * @param pageSize 页大小（默认 20，上限 50）
     * @return 流水列表（时间倒序）
     */
    List<ResourceLogVO> listResourceLogs(Long userId, ResourceLogType type, Long cursor, Integer pageSize);

    /**
     * 心愿打卡时：total_checkin_days +1（文档 6.5：打卡时 +1，历史事实只增不减）。
     * 必须与 wish_checkin 插入同事务。
     *
     * @param userId 用户 ID
     */
    void incrementOnWishCheckin(Long userId);

    /**
     * 等级提升检测（文档 6.5：基于累计行为指标，与星光余额独立）。
     *
     * <p>按 total_wishes / total_checkin_days / total_fulfilled / total_helped
     * 计算当前可达等级，仅当高于 {@code highest_level} 时同步升级 level +
     * highest_level + level_title（只升不降），并返回提升事件；否则返回 null。
     * mall-job 每日扫描与本方法共用同一判定规则（结果幂等）。</p>
     *
     * <p>必须在调用方事务上下文中执行（如签到事务内），保证升级与触发动作原子。</p>
     *
     * @param userId 用户 ID
     * @return 等级提升事件（未提升返回 null）
     */
    LevelUpVO checkAndLevelUp(Long userId);

    /**
     * 我的等级与晋级进度（文档 L1930：等级查询返回 level + level_title + 距下一级进度）。
     *
     * <p>四端「心愿殿堂/等级进度条」统一数据源：当前等级取 highest_level
     * （只升不降口径）；满级（L5）时 nextLevel 为 null、进度列表为空。
     * 只读：统计记录不存在时按 L1 初始态返回（不落记录）。</p>
     *
     * @param userId 用户 ID
     * @return 等级 + 累计指标 + 距下一级各维度进度
     */
    MyLevelVO getMyLevel(Long userId);
}

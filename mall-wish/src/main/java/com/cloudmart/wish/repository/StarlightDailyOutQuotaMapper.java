package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.StarlightDailyOutQuota;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/** 星光日转出额度 Mapper（R06 原子占用）。 */
public interface StarlightDailyOutQuotaMapper extends BaseMapper<StarlightDailyOutQuota> {

    /**
     * R06/R28：日额度原子占用（单语句 upsert + 条件自增）。
     *
     * <p>必须是<b>单条</b> INSERT ... ON DUPLICATE KEY UPDATE：早高峰同一用户首转
     * 并发时，旧写法「先 INSERT（重复键报错）再 UPDATE」会让竞争失败的事务在
     * 唯一索引记录上滞留 S 锁，随后同一事务内的 UPDATE 申请 X 锁形成
     * S 锁队列→X 锁升级环，InnoDB 主动死锁回滚（AT12/AT13 50 并发实测复现）。
     * 单语句 upsert 让所有并发在权威行上按记录锁串行，无跨语句锁升级。</p>
     *
     * <p>占用结果不依赖 affected rows 判定：Connector/J 默认 found-rows 语义下
     * 「未变更的既有行」也返回 1，无法区分超限拒绝。改为通过
     * {@code LAST_INSERT_ID(expr)} 会话变量回传判定标记（同事务同一连接，
     * 见 {@link #lastOccupyMarker()}）：接受→标记=新版本号（≥1），
     * 拒绝→标记=0。IF() 短路求值保证仅命中分支产生副作用。</p>
     *
     * <p>赋值顺序不可调换：version 先判定（此时 used_amount 为旧值），
     * used_amount 后更新（前序仅改 version 列，used_amount 仍为旧行值）。</p>
     *
     * @return 1=当日首行插入（必然接受，amount 上限 100 &lt; 日 cap 200）；
     *         其余=既有行更新，接受/拒绝需读 {@link #lastOccupyMarker()}
     */
    @Insert("""
            INSERT INTO starlight_daily_out_quota (user_id, business_date, used_amount, version, created_at, updated_at)
            VALUES (#{userId}, #{businessDate}, #{amount}, 0, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                version     = IF(used_amount + #{amount} <= #{dailyCap},
                                 LAST_INSERT_ID(version + 1), LAST_INSERT_ID(0)),
                used_amount = IF(used_amount + #{amount} <= #{dailyCap},
                                 used_amount + #{amount}, used_amount)
            """)
    int occupyQuota(@Param("userId") Long userId,
                    @Param("businessDate") LocalDate businessDate,
                    @Param("amount") int amount,
                    @Param("dailyCap") int dailyCap);

    /**
     * 读取本次 {@link #occupyQuota} 在会话变量中回传的占用标记。
     * 必须在同一事务（同一连接）中紧接 occupyQuota 调用。
     *
     * @return 0=占用被拒（已达日上限）；≥1=占用成功（新乐观锁版本号）
     */
    @org.apache.ibatis.annotations.Select("SELECT LAST_INSERT_ID()")
    long lastOccupyMarker();
}

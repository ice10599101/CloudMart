package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareDailyStat;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/** 软件日聚合 Mapper（SF06）：rebuild 全量精确重算，重复执行不翻倍 */
@Mapper
public interface SoftwareDailyStatMapper extends BaseMapper<SoftwareDailyStat> {

    /** 清除重算范围（与重插同事务执行） */
    @Delete("""
            DELETE FROM software_daily_stat WHERE stat_date BETWEEN #{from} AND #{to}
            """)
    int deleteRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 获取链接口径：从 software_link_access_daily 精确重算（行数=次数；去重用户） */
    @Insert("""
            INSERT INTO software_daily_stat
                (stat_date, software_id, resolve_count, resolve_user_count,
                 favorite_add, favorite_remove, subscribe_add, subscribe_remove)
            SELECT access_date, software_id, COUNT(*), COUNT(DISTINCT user_id),
                   0, 0, 0, 0
            FROM software_link_access_daily
            WHERE access_date BETWEEN #{from} AND #{to}
            GROUP BY access_date, software_id
            """)
    int insertResolveRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 收藏/订阅口径：从事件表合并（与 resolve 行 UPSERT 合并；无 resolve 行也成行） */
    @Insert("""
            INSERT INTO software_daily_stat
                (stat_date, software_id, resolve_count, resolve_user_count,
                 favorite_add, favorite_remove, subscribe_add, subscribe_remove)
            SELECT event_date, software_id, 0, 0,
                SUM(kind = 'FAVORITE_ADD'), SUM(kind = 'FAVORITE_REMOVE'),
                SUM(kind = 'SUBSCRIBE_ADD'), SUM(kind = 'SUBSCRIBE_REMOVE')
            FROM software_stat_event
            WHERE event_date BETWEEN #{from} AND #{to}
            GROUP BY event_date, software_id
            ON DUPLICATE KEY UPDATE
                favorite_add = VALUES(favorite_add),
                favorite_remove = VALUES(favorite_remove),
                subscribe_add = VALUES(subscribe_add),
                subscribe_remove = VALUES(subscribe_remove)
            """)
    int insertFavoriteSubscribeRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}

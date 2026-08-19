package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.entity.WishInteraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WishInteractionMapper extends BaseMapper<WishInteraction> {

    /**
     * 统计心愿自指定时间起的指定类型互动总数（含已软删记录，绕过逻辑删除过滤）。
     *
     * <p>用途：作者星光日上限判定（文档 6.1 节）——被点亮每日上限 20、被同求每日上限 50。
     * 必须包含软删记录：取消互动不退还已发放星光，若按未删除记录计数，
     * "取消→重新互动"会重复触发作者星光发放（刷星光漏洞）。</p>
     *
     * @param wishId 心愿 ID
     * @param type   互动类型（枚举名）
     * @param since  起始时间（当日 0 点，平台运营时区）
     * @return 互动总次数（含软删）
     */
    @Select("SELECT COUNT(*) FROM wish_interaction WHERE wish_id = #{wishId} "
            + "AND type = #{type} AND created_at >= #{since}")
    long countIncludingDeletedSince(@Param("wishId") Long wishId,
                                    @Param("type") String type,
                                    @Param("since") java.time.LocalDateTime since);

    /**
     * 管理后台分页查询（含已取消互动，绕过逻辑删除过滤，保留完整审计轨迹）。
     *
     * @param page    分页参数
     * @param wrapper 动态筛选条件
     * @return 互动分页（含软删）
     */
    @Select("SELECT * FROM wish_interaction ${ew.customSqlSegment}")
    Page<WishInteraction> selectPageIncludingDeleted(Page<WishInteraction> page,
                                                     @Param(Constants.WRAPPER) Wrapper<WishInteraction> wrapper);
}

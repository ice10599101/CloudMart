package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.Wish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WishMapper extends BaseMapper<Wish> {

    /**
     * 批量查询心愿（含已软删，绕过逻辑删除过滤）。
     *
     * <p>用途：管理后台互动/评论列表关联心愿标题——互动记录的心愿可能已被作者删除，
     * 审计场景仍需展示标题，不能用 selectBatchIds（会被 @TableLogic 过滤）。</p>
     *
     * @param ids 心愿 ID 集合
     * @return 心愿列表（含软删）
     */
    @Select("<script>SELECT * FROM wish WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Wish> selectBatchIdsIncludingDeleted(@Param("ids") Collection<Long> ids);
}

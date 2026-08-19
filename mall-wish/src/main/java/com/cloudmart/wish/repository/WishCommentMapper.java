package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.entity.WishComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WishCommentMapper extends BaseMapper<WishComment> {

    /**
     * 管理后台分页查询（含已软删评论，绕过逻辑删除过滤，保留完整审计轨迹）。
     *
     * @param page    分页参数
     * @param wrapper 动态筛选条件
     * @return 评论分页（含软删）
     */
    @Select("SELECT * FROM wish_comment ${ew.customSqlSegment}")
    Page<WishComment> selectPageIncludingDeleted(Page<WishComment> page,
                                                 @Param(Constants.WRAPPER) Wrapper<WishComment> wrapper);
}

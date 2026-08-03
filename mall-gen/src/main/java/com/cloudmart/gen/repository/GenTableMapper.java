package com.cloudmart.gen.repository;

import com.cloudmart.gen.dto.GenTableColumnResponse;
import com.cloudmart.gen.dto.GenTableResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GenTableMapper {

    @Select("SELECT table_name, table_comment, create_time, update_time " +
            "FROM information_schema.tables " +
            "WHERE table_schema = (SELECT DATABASE()) " +
            "AND table_name NOT LIKE 'QRTZ_%' " +
            "AND table_name NOT LIKE 'gen_%' " +
            "ORDER BY create_time DESC")
    List<GenTableResponse> selectTableList();

    @Select("SELECT table_name, table_comment, create_time, update_time " +
            "FROM information_schema.tables " +
            "WHERE table_schema = (SELECT DATABASE()) " +
            "AND table_name = #{tableName}")
    GenTableResponse selectTableByName(@Param("tableName") String tableName);

    @Select("SELECT column_name, column_comment, column_type, column_key, is_nullable, " +
            "column_default, extra, ordinal_position, '' as java_type, '' as java_field " +
            "FROM information_schema.columns " +
            "WHERE table_schema = (SELECT DATABASE()) " +
            "AND table_name = #{tableName} " +
            "ORDER BY ordinal_position")
    List<GenTableColumnResponse> selectTableColumns(@Param("tableName") String tableName);
}

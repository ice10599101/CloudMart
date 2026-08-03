package com.cloudmart.admin.converter;

import com.cloudmart.admin.dto.*;
import com.cloudmart.admin.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdminConverter {

    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "posts", ignore = true)
    AdminUserResponse toUserResponse(AdminUser entity);

    List<AdminUserResponse> toUserResponseList(List<AdminUser> entities);

    AdminRoleResponse toRoleResponse(AdminRole entity);

    List<AdminRoleResponse> toRoleResponseList(List<AdminRole> entities);

    @Mapping(target = "children", ignore = true)
    AdminMenuResponse toMenuResponse(AdminMenu entity);

    List<AdminMenuResponse> toMenuResponseList(List<AdminMenu> entities);

    @Mapping(target = "children", ignore = true)
    AdminDeptResponse toDeptResponse(AdminDept entity);

    List<AdminDeptResponse> toDeptResponseList(List<AdminDept> entities);

    AdminPostResponse toPostResponse(AdminPost entity);

    List<AdminPostResponse> toPostResponseList(List<AdminPost> entities);

    AdminDictTypeResponse toDictTypeResponse(AdminDictType entity);

    List<AdminDictTypeResponse> toDictTypeResponseList(List<AdminDictType> entities);

    AdminDictDataResponse toDictDataResponse(AdminDictData entity);

    List<AdminDictDataResponse> toDictDataResponseList(List<AdminDictData> entities);

    AdminConfigResponse toConfigResponse(AdminConfig entity);

    List<AdminConfigResponse> toConfigResponseList(List<AdminConfig> entities);

    @Mapping(target = "readCount", ignore = true)
    @Mapping(target = "isRead", ignore = true)
    AdminNoticeResponse toNoticeResponse(AdminNotice entity);

    List<AdminNoticeResponse> toNoticeResponseList(List<AdminNotice> entities);

    AdminOperLogResponse toOperLogResponse(AdminOperLog entity);

    List<AdminOperLogResponse> toOperLogResponseList(List<AdminOperLog> entities);

    AdminLoginLogResponse toLoginLogResponse(AdminLoginLog entity);

    List<AdminLoginLogResponse> toLoginLogResponseList(List<AdminLoginLog> entities);
}

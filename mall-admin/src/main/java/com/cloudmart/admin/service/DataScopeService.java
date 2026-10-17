package com.cloudmart.admin.service;

import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.datascope.DataScopeHandler;
import com.cloudmart.common.datascope.DataScopeResult;
import com.cloudmart.common.datascope.DataScopeType;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 数据范围（ADMIN-01 组织模型退役后）：只保留两级边界——
 * 超级管理员 = ALL；其余管理员 = SELF（当前账号边界）。
 * 跨用户操作能力由角色操作权限点决定，不再按部门/岗位划分数据范围。
 */
@Service
public class DataScopeService implements DataScopeHandler {

    @Override
    public DataScopeResult resolveDataScope() {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx != null && ctx.isSuperAdmin()) {
            return new DataScopeResult(DataScopeType.ALL, Collections.emptyList());
        }
        return new DataScopeResult(DataScopeType.SELF, Collections.emptyList());
    }

    /** ADMIN-01：带参重载保留调用兼容（组织模型退役后语义与无参一致） */
    public DataScopeResult resolveDataScope(Long userId) {
        return resolveDataScope();
    }
}

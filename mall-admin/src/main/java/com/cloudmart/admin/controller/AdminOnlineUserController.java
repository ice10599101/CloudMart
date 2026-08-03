package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminOnlineUserResponse;
import com.cloudmart.admin.service.AdminOnlineUserService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/online")
@Tag(name = "在线用户", description = "在线用户查看与强制下线")
public class AdminOnlineUserController {

    private final AdminOnlineUserService adminOnlineUserService;

    public AdminOnlineUserController(AdminOnlineUserService adminOnlineUserService) {
        this.adminOnlineUserService = adminOnlineUserService;
    }

    @GetMapping("/list")
    @RequiresPermission("admin:online:list")
    @Operation(summary = "在线用户列表", description = "获取当前在线的管理员用户列表")
    public ApiResponse<List<AdminOnlineUserResponse>> list() {
        return ApiResponse.ok(adminOnlineUserService.list());
    }

    @DeleteMapping("/{tokenId}")
    @RequiresPermission("admin:online:forceLogout")
    @OperLog(title = "在线用户", businessType = 7)
    @Operation(summary = "强制下线", description = "强制指定在线用户下线")
    public ApiResponse<Void> forceLogout(@PathVariable("tokenId") String tokenId) {
        adminOnlineUserService.forceLogout(tokenId);
        return ApiResponse.ok(null);
    }
}

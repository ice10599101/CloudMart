package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.AdminUpdateUserRequest;
import com.cloudmart.admin.dto.feign.UserDTO;
import com.cloudmart.admin.feign.MemberUserFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务管理-会员域：前台用户查询、编辑、启停、重置密码。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：会员管理")
public class AdminMemberController {

    private final MemberUserFeignClient memberUserFeignClient;

    public AdminMemberController(MemberUserFeignClient memberUserFeignClient) {
        this.memberUserFeignClient = memberUserFeignClient;
    }

    // ==================== 会员 ====================

    @GetMapping("/members")
    @RequiresPermission("business:member:list")
    @Operation(summary = "会员列表", description = "查询前台用户列表")
    public ApiResponse<List<UserDTO>> listMembers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int size,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "status", required = false) Integer status) {
        return memberUserFeignClient.listUsers(page, size, username, nickname, status);
    }

    @GetMapping("/members/{id}")
    @RequiresPermission("business:member:query")
    @Operation(summary = "会员详情", description = "查询前台用户详情")
    public ApiResponse<UserDTO> getMember(@PathVariable Long id) {
        return memberUserFeignClient.getUserById(id);
    }

    @PutMapping("/members/{id}")
    @OperLog(title = "会员管理", businessType = 2)
    @RequiresPermission("business:member:edit")
    @Operation(summary = "编辑会员信息", description = "管理员编辑会员昵称、手机号、邮箱")
    public ApiResponse<UserDTO> updateMember(@PathVariable Long id, @RequestBody AdminUpdateUserRequest request) {
        return memberUserFeignClient.updateUser(id, request);
    }

    @PutMapping("/members/{id}/status")
    @OperLog(title = "会员管理", businessType = 2)
    @RequiresPermission("business:member:edit")
    @Operation(summary = "切换会员状态", description = "启用或禁用会员账号")
    public ApiResponse<Void> toggleMemberStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return memberUserFeignClient.toggleUserStatus(id, body.get("status"));
    }

    @PutMapping("/members/{id}/password")
    @OperLog(title = "会员管理", businessType = 2)
    @RequiresPermission("business:member:edit")
    @Operation(summary = "重置会员密码", description = "管理后台直接设置用户新密码（无需原密码）")
    public ApiResponse<Void> resetMemberPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return memberUserFeignClient.resetPassword(id, body);
    }
}

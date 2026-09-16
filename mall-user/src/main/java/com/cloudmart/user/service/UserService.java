package com.cloudmart.user.service;

import com.cloudmart.user.dto.ChangeNicknameRequest;
import com.cloudmart.user.dto.ChangePasswordRequest;
import com.cloudmart.user.dto.RegisterRequest;
import com.cloudmart.user.dto.UpdateProfileRequest;
import com.cloudmart.user.dto.UserDTO;
import com.cloudmart.user.dto.ValidateRequest;
import com.cloudmart.user.vo.UserVO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface UserService {

    UserVO register(RegisterRequest request);

    UserVO getUserById(Long id);

    /**
     * 获取他人资料（含敏感字段脱敏）。
     *
     * <p>viewerId 为查看者 ID；为 null（内部调用）或与目标相同（本人）时不做脱敏。
     * 否则依据目标用户对生日/邮箱的可见性设置，隐藏不可见的敏感字段。</p>
     */
    UserVO getUserProfile(Long id, Long viewerId);

    UserDTO validateUser(ValidateRequest request);

    Page<UserVO> listUsers(int page, int size, String username, String nickname, Integer status);

    /** 推荐用户列表（对外发现接口）：隐藏邮箱/生日/星座等敏感字段 */
    List<UserVO> recommendUsers(int limit);

    /** 管理员编辑用户资料：全字段直改，昵称/邮箱唯一性校验排除自身，不受用户侧昵称冷却限制 */
    UserVO adminUpdateUser(Long userId, UpdateProfileRequest request);

    /** 管理员重置用户密码：无需原密码，BCrypt 编码落库 */
    void adminResetPassword(Long userId, String newPassword);

    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    UserVO changeNickname(Long userId, ChangeNicknameRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    void toggleUserStatus(Long id, Integer status);

    long getMemberCount();

    List<UserVO> batchGetUsers(List<Long> ids);

    List<UserVO> searchUsers(String keyword, int page, int pageSize);
}

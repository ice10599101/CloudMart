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

    UserDTO validateUser(ValidateRequest request);

    Page<UserVO> listUsers(int page, int size);

    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    UserVO changeNickname(Long userId, ChangeNicknameRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    void toggleUserStatus(Long id, Integer status);

    long getMemberCount();

    List<UserVO> batchGetUsers(List<Long> ids);

    List<UserVO> searchUsers(String keyword, int page, int pageSize);
}

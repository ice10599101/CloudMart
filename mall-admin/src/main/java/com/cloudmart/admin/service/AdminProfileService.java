package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminProfileResponse;

public interface AdminProfileService {

    AdminProfileResponse getProfile(Long userId);

    void updateProfile(Long userId, String nickname, String email, String phone, String avatar);

    void updatePassword(Long userId, String oldPassword, String newPassword);
}

package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminOnlineUserResponse;

import java.util.List;

public interface AdminOnlineUserService {

    List<AdminOnlineUserResponse> list();

    void forceLogout(String tokenId);
}

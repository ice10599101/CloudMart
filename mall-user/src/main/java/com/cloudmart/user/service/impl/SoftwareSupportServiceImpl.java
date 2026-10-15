package com.cloudmart.user.service.impl;

import com.cloudmart.user.service.SoftwareSupportService;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import com.cloudmart.user.vo.software.SoftwareProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 小答软件域用户资料适配实现：直接复用 UserService.batchGetUsers
 * （上限 100、脱敏、仅返回存在用户），再裁剪为最小字段集。
 */
@Service
@RequiredArgsConstructor
public class SoftwareSupportServiceImpl implements SoftwareSupportService {

    private final UserService userService;

    @Override
    public List<SoftwareProfileVO> profiles(List<Long> userIds) {
        List<UserVO> users = userService.batchGetUsers(userIds);
        return users.stream()
                .map(user -> new SoftwareProfileVO(user.id(), user.nickname(), user.avatar(),
                        user.status() != null && user.status() == 1))
                .toList();
    }
}

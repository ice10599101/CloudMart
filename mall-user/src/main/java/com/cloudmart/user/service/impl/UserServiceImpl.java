package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.converter.UserConverter;
import com.cloudmart.user.dto.*;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.repository.UserMapper;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final PasswordEncoder passwordEncoder;

    private static final long NICKNAME_COOLDOWN_DAYS = 7;

    @Override
    @SentinelResource(value = "register", fallback = "registerFallback")
    @Transactional
    public UserVO register(RegisterRequest request) {
        checkEmailUniqueness(request.email());
        checkNicknameUniqueness(request.nickname());

        User user = new User();
        user.setUsername(generateXiaoDaHao());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setNickname(request.nickname());
        user.setStatus(1);

        userMapper.insert(user);
        return userConverter.toVO(user);
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return userConverter.toVO(user);
    }

    @Override
    public UserDTO validateUser(ValidateRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.account())
                        .or()
                        .eq(User::getEmail, request.account())
        );
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "账号或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException("USER_DISABLED", "用户已被禁用");
        }
        return userConverter.toDTO(user);
    }

    @Override
    public Page<UserVO> listUsers(int page, int size) {
        Page<User> userPage = userMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt)
        );
        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream().map(userConverter::toVO).toList());
        return voPage;
    }

    @Override
    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        if (request.avatar() != null) user.setAvatar(request.avatar());
        if (request.signature() != null) user.setSignature(request.signature());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.birthday() != null) user.setBirthday(request.birthday());
        if (request.constellation() != null) user.setConstellation(request.constellation());
        if (request.occupation() != null) user.setOccupation(request.occupation());
        if (request.school() != null) user.setSchool(request.school());
        if (request.location() != null) user.setLocation(request.location());
        if (request.hobbies() != null) user.setHobbies(request.hobbies());

        userMapper.updateById(user);
        return userConverter.toVO(user);
    }

    @Override
    @Transactional
    public UserVO changeNickname(Long userId, ChangeNicknameRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        if (user.getNicknameUpdatedAt() != null) {
            long daysSinceLastChange = ChronoUnit.DAYS.between(user.getNicknameUpdatedAt(), LocalDateTime.now());
            if (daysSinceLastChange < NICKNAME_COOLDOWN_DAYS) {
                long remainingDays = NICKNAME_COOLDOWN_DAYS - daysSinceLastChange;
                throw new BusinessException("NICKNAME_COOLDOWN",
                        "昵称修改冷却中，还需等待" + remainingDays + "天");
            }
        }

        if (request.nickname().equals(user.getNickname())) {
            throw new BusinessException("NICKNAME_SAME", "新昵称与当前昵称相同");
        }

        checkNicknameUniqueness(request.nickname());

        user.setNickname(request.nickname());
        user.setNicknameUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return userConverter.toVO(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException("OLD_PASSWORD_WRONG", "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    @Override
    public void toggleUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public long getMemberCount() {
        return userMapper.selectCount(null);
    }

    @Override
    public List<UserVO> batchGetUsers(List<Long> ids) {
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream().map(userConverter::toVO).toList();
    }

    @Override
    public List<UserVO> searchUsers(String keyword, int page, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(User::getUsername, keyword)
                .or().like(User::getNickname, keyword)
                .or().like(User::getEmail, keyword)
                .orderByDesc(User::getCreatedAt);
        Page<User> userPage = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return userPage.getRecords().stream().map(userConverter::toVO).toList();
    }

    private void checkEmailUniqueness(String email) {
        if (email == null || email.isBlank()) return;
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        if (count > 0) {
            throw new BusinessException("EMAIL_DUPLICATE", "邮箱已被注册");
        }
    }

    private void checkNicknameUniqueness(String nickname) {
        if (nickname == null || nickname.isBlank()) return;
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getNickname, nickname)
        );
        if (count > 0) {
            throw new BusinessException("NICKNAME_DUPLICATE", "昵称已被使用");
        }
    }

    private String generateXiaoDaHao() {
        Long maxXiaoDaHao = userMapper.selectMaxXiaoDaHao();
        long nextId = (maxXiaoDaHao != null ? maxXiaoDaHao : 9999) + 1;
        return String.valueOf(nextId);
    }

    public UserVO registerFallback(RegisterRequest request, Throwable throwable) {
        throw new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
    }
}

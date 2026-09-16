package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.converter.UserConverter;
import com.cloudmart.user.dto.*;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.feign.CommunityFeignClient;
import com.cloudmart.user.repository.UserMapper;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final PasswordEncoder passwordEncoder;
    private final CommunityFeignClient communityFeignClient;

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
    public UserVO getUserProfile(Long id, Long viewerId) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        UserVO vo = userConverter.toVO(user);

        // 本人查看自身：完整数据；他人/匿名查看：按可见性脱敏。
        // 匿名时 viewerId 为 null，applyPrivacyMask 按陌生人档位处理（安全优先）。
        if (viewerId != null && viewerId.equals(id)) {
            return vo;
        }
        return applyPrivacyMask(vo, viewerId);
    }

    /**
     * 按查看者与目标用户关系对生日/邮箱/星座做脱敏。
     *
     * <p>仅用于单用户资料详情接口；列表/发现类接口（搜索、推荐）不逐条走此逻辑，
     * 直接隐藏敏感字段（见 {@link #maskEmailBirthday(UserVO)}）。</p>
     */
    private UserVO applyPrivacyMask(UserVO vo, Long viewerId) {
        boolean birthdayVisible;
        boolean emailVisible;
        try {
            ApiResponse<Map<String, Object>> resp = communityFeignClient.getPrivacyVisibility(vo.id());
            Map<String, Object> data = resp != null ? resp.data() : null;
            birthdayVisible = data != null && Boolean.TRUE.equals(data.get("birthdayVisible"));
            emailVisible = data != null && Boolean.TRUE.equals(data.get("emailVisible"));
        } catch (Exception e) {
            log.warn("查询资料可见性失败，隐私字段默认隐藏, target={}, viewer={}", vo.id(), viewerId, e);
            birthdayVisible = false;
            emailVisible = false;
        }

        if (birthdayVisible && emailVisible) {
            return vo;
        }
        return new UserVO(
                vo.id(), vo.username(), vo.nickname(),
                emailVisible ? vo.email() : null,
                vo.avatar(), vo.signature(), vo.gender(),
                birthdayVisible ? vo.birthday() : null,
                birthdayVisible ? vo.constellation() : null,
                vo.occupation(), vo.school(), vo.location(), vo.hobbies(),
                vo.status(), vo.nicknameUpdatedAt(), vo.createdAt());
    }

    /**
     * 列表/发现接口（搜索、推荐）的敏感字段脱敏：隐藏邮箱、生日、星座。
     *
     * <p>这些接口按列表返回用户，不逐条查询可见性关系，统一隐藏敏感字段，
     * 避免在结果列表中泄露他人邮箱/生日。</p>
     */
    private UserVO maskEmailBirthday(UserVO vo) {
        return new UserVO(
                vo.id(), vo.username(), vo.nickname(),
                null,
                vo.avatar(), vo.signature(), vo.gender(),
                null, null,
                vo.occupation(), vo.school(), vo.location(), vo.hobbies(),
                vo.status(), vo.nicknameUpdatedAt(), vo.createdAt());
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
    public Page<UserVO> listUsers(int page, int size, String username, String nickname, Integer status) {
        Page<User> userPage = userMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<User>()
                        .like(username != null && !username.isBlank(), User::getUsername, username)
                        .like(nickname != null && !nickname.isBlank(), User::getNickname, nickname)
                        .eq(status != null, User::getStatus, status)
                        .orderByDesc(User::getCreatedAt)
        );
        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream().map(userConverter::toVO).toList());
        return voPage;
    }

    @Override
    public List<UserVO> recommendUsers(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return listUsers(1, safeLimit, null, null, null).getRecords().stream()
                .map(this::maskEmailBirthday)
                .toList();
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

    /**
     * 管理员全字段编辑用户资料。与用户侧 updateProfile 的差异：
     * 1. 允许修改昵称/邮箱（用户侧走独立端点且昵称有冷却期）；2. 唯一性校验排除自身；
     * 3. 不触碰 nicknameUpdatedAt（管理员纠错不应触发用户侧冷却计时）。
     */
    @Override
    @Transactional
    public UserVO adminUpdateUser(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        if (request.nickname() != null && !request.nickname().isBlank()
                && !request.nickname().equals(user.getNickname())) {
            long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, request.nickname())
                    .ne(User::getId, userId));
            if (count > 0) {
                throw new BusinessException("NICKNAME_DUPLICATE", "昵称已被使用");
            }
            user.setNickname(request.nickname());
        }
        if (request.email() != null) {
            // uk_email 唯一索引：空值统一置 null（可多条），非空需唯一且排除自身
            if (request.email().isBlank()) {
                user.setEmail(null);
            } else if (!request.email().equals(user.getEmail())) {
                long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, request.email())
                        .ne(User::getId, userId));
                if (count > 0) {
                    throw new BusinessException("EMAIL_DUPLICATE", "邮箱已被使用");
                }
                user.setEmail(request.email());
            }
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
    public void adminResetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("PASSWORD_TOO_SHORT", "新密码至少 6 位");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
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
        return userPage.getRecords().stream()
                .map(userConverter::toVO)
                .map(this::maskEmailBirthday)
                .toList();
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

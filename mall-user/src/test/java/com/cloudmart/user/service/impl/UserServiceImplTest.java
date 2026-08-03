package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.converter.UserConverter;
import com.cloudmart.user.dto.*;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.repository.UserMapper;
import com.cloudmart.user.vo.UserVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserMapper userMapper;
    private UserConverter userConverter;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(User.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.user.repository.UserMapper");
            TableInfoHelper.initTableInfo(assistant, User.class);
        }
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userConverter = mock(UserConverter.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserServiceImpl(userMapper, userConverter, passwordEncoder);
    }

    private User buildActiveUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("10001");
        user.setPassword("encodedPwd");
        user.setEmail("test@example.com");
        user.setNickname("Tester");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return user;
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("email not exists -> creates user and returns UserVO")
        void register_WhenEmailNotExists_ShouldCreateUser() {
            RegisterRequest request = new RegisterRequest("password123", "test@example.com", "Tester");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(userMapper.selectMaxXiaoDaHao()).thenReturn(10000L);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPwd");

            UserVO expected = new UserVO(1L, "10001", "Tester", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, LocalDateTime.now());
            when(userConverter.toVO(any(User.class))).thenReturn(expected);

            UserVO result = userService.register(request);

            assertThat(result).isEqualTo(expected);
            verify(userMapper).insert(any(User.class));
            verify(passwordEncoder).encode("password123");
        }

        @Test
        @DisplayName("email exists -> throws EMAIL_DUPLICATE")
        void register_WhenEmailExists_ShouldThrowBusinessException() {
            RegisterRequest request = new RegisterRequest("password123", "dup@example.com", "Dup");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("EMAIL_DUPLICATE"));
        }

        @Test
        @DisplayName("nickname exists -> throws NICKNAME_DUPLICATE")
        void register_WhenNicknameExists_ShouldThrowBusinessException() {
            RegisterRequest request = new RegisterRequest("password123", "new@example.com", "DupNick");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L)
                    .thenReturn(1L);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NICKNAME_DUPLICATE"));
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("user exists -> returns UserVO")
        void getUserById_WhenUserExists_ShouldReturnUserVO() {
            User user = buildActiveUser();
            when(userMapper.selectById(1L)).thenReturn(user);

            UserVO expected = new UserVO(1L, "10001", "Tester", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, LocalDateTime.of(2026, 1, 1, 0, 0));
            when(userConverter.toVO(user)).thenReturn(expected);

            UserVO result = userService.getUserById(1L);

            assertThat(result).isEqualTo(expected);
            verify(userMapper).selectById(1L);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void getUserById_WhenUserNotFound_ShouldThrowBusinessException() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.getUserById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("validateUser")
    class ValidateUserTests {

        @Test
        @DisplayName("valid credentials -> returns UserDTO")
        void validateUser_WhenValidCredentials_ShouldReturnUserDTO() {
            User user = buildActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("password123", "encodedPwd")).thenReturn(true);

            UserDTO expected = new UserDTO(1L, "10001", "test@example.com", "Tester", null, null, null, null, null, null, null, null, 1, null, LocalDateTime.of(2026, 1, 1, 0, 0));
            when(userConverter.toDTO(user)).thenReturn(expected);

            ValidateRequest request = new ValidateRequest("test@example.com", "password123");
            UserDTO result = userService.validateUser(request);

            assertThat(result).isEqualTo(expected);
            verify(userConverter).toDTO(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void validateUser_WhenUserNotFound_ShouldThrowBusinessException() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            ValidateRequest request = new ValidateRequest("nobody@example.com", "password123");

            assertThatThrownBy(() -> userService.validateUser(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("wrong password -> throws INVALID_CREDENTIALS")
        void validateUser_WhenWrongPassword_ShouldThrowBusinessException() {
            User user = buildActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("wrongPwd", "encodedPwd")).thenReturn(false);

            ValidateRequest request = new ValidateRequest("test@example.com", "wrongPwd");

            assertThatThrownBy(() -> userService.validateUser(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("disabled user -> throws USER_DISABLED")
        void validateUser_WhenUserDisabled_ShouldThrowBusinessException() {
            User user = buildActiveUser();
            user.setStatus(0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("password123", "encodedPwd")).thenReturn(true);

            ValidateRequest request = new ValidateRequest("test@example.com", "password123");

            assertThatThrownBy(() -> userService.validateUser(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_DISABLED"));
        }
    }

    @Nested
    @DisplayName("changeNickname")
    class ChangeNicknameTests {

        @Test
        @DisplayName("within cooldown period -> throws NICKNAME_COOLDOWN")
        void changeNickname_WithinCooldownPeriod_ShouldThrowBusinessException() {
            User user = buildActiveUser();
            user.setNicknameUpdatedAt(LocalDateTime.now().minusDays(3));
            when(userMapper.selectById(1L)).thenReturn(user);

            ChangeNicknameRequest request = new ChangeNicknameRequest("NewNick");

            assertThatThrownBy(() -> userService.changeNickname(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NICKNAME_COOLDOWN"));
        }

        @Test
        @DisplayName("same nickname -> throws NICKNAME_SAME")
        void changeNickname_SameNickname_ShouldThrowBusinessException() {
            User user = buildActiveUser();
            user.setNicknameUpdatedAt(LocalDateTime.now().minusDays(10));
            when(userMapper.selectById(1L)).thenReturn(user);

            ChangeNicknameRequest request = new ChangeNicknameRequest("Tester");

            assertThatThrownBy(() -> userService.changeNickname(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NICKNAME_SAME"));
        }

        @Test
        @DisplayName("nickname already taken -> throws NICKNAME_DUPLICATE")
        void changeNickname_NicknameTaken_ShouldThrowBusinessException() {
            User user = buildActiveUser();
            user.setNicknameUpdatedAt(LocalDateTime.now().minusDays(10));
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            ChangeNicknameRequest request = new ChangeNicknameRequest("TakenNick");

            assertThatThrownBy(() -> userService.changeNickname(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NICKNAME_DUPLICATE"));
        }

        @Test
        @DisplayName("no previous change (null nicknameUpdatedAt) -> succeeds")
        void changeNickname_NoPreviousChange_ShouldSucceed() {
            User user = buildActiveUser();
            user.setNicknameUpdatedAt(null);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            UserVO expected = new UserVO(1L, "10001", "NewNick", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, LocalDateTime.of(2026, 1, 1, 0, 0));
            when(userConverter.toVO(user)).thenReturn(expected);

            ChangeNicknameRequest request = new ChangeNicknameRequest("NewNick");
            UserVO result = userService.changeNickname(1L, request);

            assertThat(result).isEqualTo(expected);
            verify(userMapper).updateById(user);
            assertThat(user.getNickname()).isEqualTo("NewNick");
            assertThat(user.getNicknameUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("cooldown expired -> succeeds")
        void changeNickname_CooldownExpired_ShouldSucceed() {
            User user = buildActiveUser();
            user.setNicknameUpdatedAt(LocalDateTime.now().minusDays(8));
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            UserVO expected = new UserVO(1L, "10001", "NewNick", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, LocalDateTime.of(2026, 1, 1, 0, 0));
            when(userConverter.toVO(user)).thenReturn(expected);

            ChangeNicknameRequest request = new ChangeNicknameRequest("NewNick");
            UserVO result = userService.changeNickname(1L, request);

            assertThat(result).isEqualTo(expected);
            verify(userMapper).updateById(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void changeNickname_UserNotFound_ShouldThrowBusinessException() {
            when(userMapper.selectById(999L)).thenReturn(null);

            ChangeNicknameRequest request = new ChangeNicknameRequest("NewNick");

            assertThatThrownBy(() -> userService.changeNickname(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("correct old password -> updates password")
        void changePassword_CorrectOldPassword_ShouldUpdate() {
            User user = buildActiveUser();
            when(userMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.matches("oldPwd", "encodedPwd")).thenReturn(true);
            when(passwordEncoder.encode("newPwd")).thenReturn("newEncodedPwd");

            ChangePasswordRequest request = new ChangePasswordRequest("oldPwd", "newPwd");
            userService.changePassword(1L, request);

            assertThat(user.getPassword()).isEqualTo("newEncodedPwd");
            verify(userMapper).updateById(user);
        }

        @Test
        @DisplayName("wrong old password -> throws OLD_PASSWORD_WRONG")
        void changePassword_WrongOldPassword_ShouldThrowBusinessException() {
            User user = buildActiveUser();
            when(userMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.matches("wrongPwd", "encodedPwd")).thenReturn(false);

            ChangePasswordRequest request = new ChangePasswordRequest("wrongPwd", "newPwd");

            assertThatThrownBy(() -> userService.changePassword(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("OLD_PASSWORD_WRONG"));
            verify(userMapper, never()).updateById(any(User.class));
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void changePassword_UserNotFound_ShouldThrowBusinessException() {
            when(userMapper.selectById(999L)).thenReturn(null);

            ChangePasswordRequest request = new ChangePasswordRequest("oldPwd", "newPwd");

            assertThatThrownBy(() -> userService.changePassword(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("user exists with fields -> updates profile and returns UserVO")
        void updateProfile_UserExists_ShouldUpdateAndReturnVO() {
            User user = buildActiveUser();
            when(userMapper.selectById(1L)).thenReturn(user);

            UserVO expected = new UserVO(1L, "10001", "Tester", "test@example.com", "newAvatar.png", "Hello!", "M", "2000-01-01", null, null, null, null, null, 1, null, LocalDateTime.of(2026, 1, 1, 0, 0));
            when(userConverter.toVO(user)).thenReturn(expected);

            UpdateProfileRequest request = new UpdateProfileRequest(null, null, "newAvatar.png", "Hello!", "M", "2000-01-01", null, null, null, null, null);
            UserVO result = userService.updateProfile(1L, request);

            assertThat(result).isEqualTo(expected);
            assertThat(user.getAvatar()).isEqualTo("newAvatar.png");
            assertThat(user.getSignature()).isEqualTo("Hello!");
            assertThat(user.getGender()).isEqualTo("M");
            assertThat(user.getBirthday()).isEqualTo("2000-01-01");
            verify(userMapper).updateById(user);
        }

        @Test
        @DisplayName("all null fields -> does not update any field")
        void updateProfile_AllNullFields_ShouldNotUpdateFields() {
            User user = buildActiveUser();
            user.setAvatar("original.png");
            user.setSignature("original sig");
            when(userMapper.selectById(1L)).thenReturn(user);

            UserVO expected = new UserVO(1L, "10001", "Tester", "test@example.com", "original.png", "original sig", null, null, null, null, null, null, null, 1, null, LocalDateTime.of(2026, 1, 1, 0, 0));
            when(userConverter.toVO(user)).thenReturn(expected);

            UpdateProfileRequest request = new UpdateProfileRequest(null, null, null, null, null, null, null, null, null, null, null);
            UserVO result = userService.updateProfile(1L, request);

            assertThat(result).isEqualTo(expected);
            assertThat(user.getAvatar()).isEqualTo("original.png");
            assertThat(user.getSignature()).isEqualTo("original sig");
            verify(userMapper).updateById(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void updateProfile_UserNotFound_ShouldThrowBusinessException() {
            when(userMapper.selectById(999L)).thenReturn(null);

            UpdateProfileRequest request = new UpdateProfileRequest(null, null, "avatar.png", null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> userService.updateProfile(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("toggleUserStatus")
    class ToggleUserStatusTests {

        @Test
        @DisplayName("user exists -> updates status")
        void toggleUserStatus_UserExists_ShouldUpdateStatus() {
            User user = buildActiveUser();
            when(userMapper.selectById(1L)).thenReturn(user);

            userService.toggleUserStatus(1L, 0);

            assertThat(user.getStatus()).isEqualTo(0);
            verify(userMapper).updateById(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void toggleUserStatus_UserNotFound_ShouldThrowBusinessException() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.toggleUserStatus(999L, 0))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getMemberCount")
    class GetMemberCountTests {

        @Test
        @DisplayName("returns count from mapper")
        void getMemberCount_ShouldReturnCount() {
            when(userMapper.selectCount(null)).thenReturn(42L);

            long result = userService.getMemberCount();

            assertThat(result).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("batchGetUsers")
    class BatchGetUsersTests {

        @Test
        @DisplayName("returns list of UserVO for given ids")
        void batchGetUsers_ShouldReturnUserVOList() {
            User user1 = buildActiveUser();
            User user2 = buildActiveUser();
            user2.setId(2L);

            when(userMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(user1, user2));

            UserVO vo1 = new UserVO(1L, "10001", "Tester", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, null);
            UserVO vo2 = new UserVO(2L, "10001", "Tester", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, null);
            when(userConverter.toVO(user1)).thenReturn(vo1);
            when(userConverter.toVO(user2)).thenReturn(vo2);

            List<UserVO> result = userService.batchGetUsers(List.of(1L, 2L));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(1).id()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("searchUsers")
    class SearchUsersTests {

        @Test
        @DisplayName("returns matching users")
        void searchUsers_ShouldReturnMatchingUsers() {
            User user = buildActiveUser();
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
            page.setRecords(List.of(user));
            when(userMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            UserVO vo = new UserVO(1L, "10001", "Tester", "test@example.com", null, null, null, null, null, null, null, null, null, 1, null, null);
            when(userConverter.toVO(user)).thenReturn(vo);

            List<UserVO> result = userService.searchUsers("test", 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nickname()).isEqualTo("Tester");
        }
    }
}

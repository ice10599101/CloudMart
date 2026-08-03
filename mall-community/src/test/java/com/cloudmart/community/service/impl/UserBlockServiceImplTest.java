package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.UserBlock;
import com.cloudmart.community.repository.UserBlockMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceImplTest {

    @Mock
    private UserBlockMapper userBlockMapper;

    private UserBlockServiceImpl userBlockService;

    private static final Long USER_ID = 1L;
    private static final Long BLOCKED_USER_ID = 2L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserBlock.class);
    }

    @BeforeEach
    void setUp() {
        userBlockService = new UserBlockServiceImpl(userBlockMapper);
    }

    private UserBlock buildUserBlock() {
        UserBlock block = new UserBlock();
        block.setId(1L);
        block.setUserId(USER_ID);
        block.setBlockedUserId(BLOCKED_USER_ID);
        return block;
    }

    @Nested
    @DisplayName("blockUser")
    class BlockUserTests {

        @Test
        @DisplayName("should throw when blocking self")
        void blockUser_selfBlock_throwsException() {
            assertThatThrownBy(() -> userBlockService.blockUser(USER_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("INVALID_BLOCK");
                    });

            verify(userBlockMapper, never()).insert(any(UserBlock.class));
        }

        @Test
        @DisplayName("should not insert when already blocked")
        void blockUser_alreadyBlocked_noInsert() {
            when(userBlockMapper.selectOne(any())).thenReturn(buildUserBlock());

            userBlockService.blockUser(USER_ID, BLOCKED_USER_ID);

            verify(userBlockMapper, never()).insert(any(UserBlock.class));
        }

        @Test
        @DisplayName("should insert block record for new block")
        void blockUser_newBlock_inserts() {
            when(userBlockMapper.selectOne(any())).thenReturn(null);

            userBlockService.blockUser(USER_ID, BLOCKED_USER_ID);

            verify(userBlockMapper).insert(any(UserBlock.class));
        }
    }

    @Nested
    @DisplayName("unblockUser")
    class UnblockUserTests {

        @Test
        @DisplayName("should delete block record by wrapper")
        void unblockUser_deletesBlock() {
            when(userBlockMapper.delete(any())).thenReturn(1);

            userBlockService.unblockUser(USER_ID, BLOCKED_USER_ID);

            verify(userBlockMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("isBlocked")
    class IsBlockedTests {

        @Test
        @DisplayName("should return true when block exists")
        void isBlocked_exists_returnsTrue() {
            when(userBlockMapper.selectCount(any())).thenReturn(1L);

            boolean result = userBlockService.isBlocked(USER_ID, BLOCKED_USER_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when block does not exist")
        void isBlocked_notExists_returnsFalse() {
            when(userBlockMapper.selectCount(any())).thenReturn(0L);

            boolean result = userBlockService.isBlocked(USER_ID, BLOCKED_USER_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getBlockedUserIds")
    class GetBlockedUserIdsTests {

        @Test
        @DisplayName("should return list of blocked user IDs")
        void getBlockedUserIds_hasBlocks_returnsIds() {
            UserBlock block1 = new UserBlock();
            block1.setBlockedUserId(2L);
            UserBlock block2 = new UserBlock();
            block2.setBlockedUserId(3L);

            when(userBlockMapper.selectList(any())).thenReturn(List.of(block1, block2));

            List<Long> result = userBlockService.getBlockedUserIds(USER_ID);

            assertThat(result).containsExactly(2L, 3L);
        }

        @Test
        @DisplayName("should return empty list when no blocks")
        void getBlockedUserIds_noBlocks_returnsEmpty() {
            when(userBlockMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<Long> result = userBlockService.getBlockedUserIds(USER_ID);

            assertThat(result).isEmpty();
        }
    }
}

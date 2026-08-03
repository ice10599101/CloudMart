package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminNoticeRequest;
import com.cloudmart.admin.entity.AdminNotice;
import com.cloudmart.admin.entity.AdminNoticeRead;
import com.cloudmart.admin.repository.AdminNoticeMapper;
import com.cloudmart.admin.repository.AdminNoticeReadMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminNoticeServiceImplTest {

    private AdminNoticeMapper adminNoticeMapper;
    private AdminNoticeReadMapper adminNoticeReadMapper;
    private AdminNoticeServiceImpl adminNoticeService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AdminNotice.class, AdminNoticeRead.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.admin.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        adminNoticeMapper = mock(AdminNoticeMapper.class);
        adminNoticeReadMapper = mock(AdminNoticeReadMapper.class);
        adminNoticeService = new AdminNoticeServiceImpl(adminNoticeMapper, adminNoticeReadMapper);
    }

    private AdminNotice buildNotice(Long id) {
        AdminNotice notice = new AdminNotice();
        notice.setId(id);
        notice.setNoticeTitle("Test Notice");
        notice.setNoticeType(1);
        notice.setNoticeContent("Content");
        notice.setStatus(0);
        notice.setRemark(null);
        return notice;
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("notice exists -> queries readCount and returns response")
        void getById_Exists_ShouldQueryReadCount() {
            AdminNotice notice = buildNotice(1L);
            when(adminNoticeMapper.selectById(1L)).thenReturn(notice);
            when(adminNoticeReadMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            var response = adminNoticeService.getById(1L);

            assertThat(response).isNotNull();
            verify(adminNoticeMapper).selectById(1L);
            verify(adminNoticeReadMapper).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("notice not found -> throws NOTICE_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminNoticeMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminNoticeService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOTICE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("valid request -> inserts notice")
        void create_ValidRequest_ShouldInsert() {
            when(adminNoticeMapper.insert(any(AdminNotice.class))).thenReturn(1);

            AdminNoticeRequest request = new AdminNoticeRequest("Title", 1, "Content", 0, null);
            adminNoticeService.create(request);

            verify(adminNoticeMapper).insert(any(AdminNotice.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("notice exists -> updates notice")
        void update_Exists_ShouldUpdate() {
            AdminNotice notice = buildNotice(1L);
            when(adminNoticeMapper.selectById(1L)).thenReturn(notice);

            AdminNoticeRequest request = new AdminNoticeRequest("Updated Title", 2, "Updated Content", 1, "remark");
            adminNoticeService.update(1L, request);

            assertThat(notice.getNoticeTitle()).isEqualTo("Updated Title");
            verify(adminNoticeMapper).updateById(notice);
        }

        @Test
        @DisplayName("notice not found -> throws NOTICE_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminNoticeMapper.selectById(999L)).thenReturn(null);

            AdminNoticeRequest request = new AdminNoticeRequest("Title", 1, "Content", 0, null);

            assertThatThrownBy(() -> adminNoticeService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOTICE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("notice exists -> deletes notice")
        void delete_Exists_ShouldDelete() {
            AdminNotice notice = buildNotice(1L);
            when(adminNoticeMapper.selectById(1L)).thenReturn(notice);

            adminNoticeService.delete(1L);

            verify(adminNoticeMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("notice not found -> throws NOTICE_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminNoticeMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminNoticeService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOTICE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTests {

        @Test
        @DisplayName("first read -> inserts read record")
        void markAsRead_FirstRead_ShouldInsert() {
            AdminNotice notice = buildNotice(1L);
            when(adminNoticeMapper.selectById(1L)).thenReturn(notice);
            when(adminNoticeReadMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            adminNoticeService.markAsRead(1L, 100L);

            verify(adminNoticeReadMapper).insert(any(AdminNoticeRead.class));
        }

        @Test
        @DisplayName("already read -> skips insert")
        void markAsRead_AlreadyRead_ShouldSkipInsert() {
            AdminNotice notice = buildNotice(1L);
            when(adminNoticeMapper.selectById(1L)).thenReturn(notice);
            when(adminNoticeReadMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            adminNoticeService.markAsRead(1L, 100L);

            verify(adminNoticeReadMapper, never()).insert(any(AdminNoticeRead.class));
        }

        @Test
        @DisplayName("notice not found -> throws NOTICE_NOT_FOUND")
        void markAsRead_NoticeNotFound_ShouldThrowBusinessException() {
            when(adminNoticeMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminNoticeService.markAsRead(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOTICE_NOT_FOUND"));
        }
    }
}

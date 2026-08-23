package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateCapsuleRequest;
import com.cloudmart.wish.entity.TimeCapsule;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.CapsuleStatus;
import com.cloudmart.wish.mq.CapsuleEventProducer;
import com.cloudmart.wish.repository.TimeCapsuleMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.UserStatService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CapsuleServiceImpl 单元测试")
class CapsuleServiceImplTest {

    @Mock
    private TimeCapsuleMapper timeCapsuleMapper;
    @Mock
    private WishUserStatMapper wishUserStatMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private CapsuleEventProducer capsuleEventProducer;

    private WishContentSanitizer contentSanitizer;
    private CapsuleServiceImpl capsuleService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long CAPSULE_ID = 2001L;

    @BeforeAll
    static void initEntityMeta() {
        // LambdaQueryWrapper/LambdaUpdateWrapper 构造期解析列名需要 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TimeCapsule.class);
        TableInfoHelper.initTableInfo(assistant, WishUserStat.class);
    }

    @BeforeEach
    void setUp() {
        contentSanitizer = new WishContentSanitizer(List.of());
        capsuleService = new CapsuleServiceImpl(
                timeCapsuleMapper, wishUserStatMapper, userStatService,
                capsuleEventProducer, contentSanitizer);
    }

    private TimeCapsule buildCapsule(CapsuleStatus status, LocalDateTime openAt) {
        TimeCapsule capsule = new TimeCapsule();
        capsule.setId(CAPSULE_ID);
        capsule.setUserId(USER_ID);
        capsule.setTitle("写给一年后的自己");
        capsule.setContent("封存的内容<script>alert(1)</script>");
        capsule.setMediaUrls("[\"oss://a.png\"]");
        capsule.setOpenAt(openAt);
        capsule.setOpenAtTimezone("Asia/Shanghai");
        capsule.setStatus(status);
        return capsule;
    }

    // ========== createCapsule ==========

    @Nested
    @DisplayName("createCapsule - 创建胶囊")
    class CreateCapsuleTests {

        @Test
        @DisplayName("创建成功：SEALED 封存 + XSS 转义 + 时区规范化 + 内容防绕过")
        void createCapsule_success() {
            CreateCapsuleRequest request = new CreateCapsuleRequest(
                    "写给<b>一年后</b>的自己", "内容", List.of("oss://a.png"),
                    LocalDateTime.now().plusDays(30), " Asia/Shanghai ");

            when(timeCapsuleMapper.insert(any(TimeCapsule.class))).thenAnswer(inv -> {
                inv.getArgument(0, TimeCapsule.class).setId(CAPSULE_ID);
                return 1;
            });

            var vo = capsuleService.createCapsule(USER_ID, request);

            assertThat(vo.id()).isEqualTo(CAPSULE_ID);
            assertThat(vo.status()).isEqualTo("SEALED");
            assertThat(vo.content()).as("非 OPENED 状态内容恒不返回（防绕过）").isNull();
            assertThat(vo.mediaUrls()).isNull();
            assertThat(vo.openAtTimezone()).isEqualTo("Asia/Shanghai");
            verify(timeCapsuleMapper).insert(org.mockito.ArgumentMatchers
                    .<TimeCapsule>argThat(c ->
                            "SEALED".equals(c.getStatus().name())
                                    && c.getTitle().equals("写给&lt;b&gt;一年后&lt;/b&gt;的自己")
                                    && "Asia/Shanghai".equals(c.getOpenAtTimezone())));
        }

        @Test
        @DisplayName("开启时间在过去 → WISH_OPEN_AT_PAST")
        void createCapsule_pastOpenAt_rejected() {
            CreateCapsuleRequest request = new CreateCapsuleRequest(
                    "标题", "内容", null, LocalDateTime.now().minusDays(1), "Asia/Shanghai");

            assertThatThrownBy(() -> capsuleService.createCapsule(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_OPEN_AT_PAST);
        }

        @Test
        @DisplayName("开启时间超过 10 年上界 → WISH_VALIDATION_ERROR")
        void createCapsule_beyondTenYears_rejected() {
            CreateCapsuleRequest request = new CreateCapsuleRequest(
                    "标题", "内容", null, LocalDateTime.now().plusDays(3651), "Asia/Shanghai");

            assertThatThrownBy(() -> capsuleService.createCapsule(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("非法 IANA 时区 → WISH_VALIDATION_ERROR")
        void createCapsule_invalidTimezone_rejected() {
            CreateCapsuleRequest request = new CreateCapsuleRequest(
                    "标题", "内容", null, LocalDateTime.now().plusDays(1), "Not/AZone");

            assertThatThrownBy(() -> capsuleService.createCapsule(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("内容含路径穿越片段 → WISH_VALIDATION_ERROR")
        void createCapsule_pathTraversal_rejected() {
            CreateCapsuleRequest request = new CreateCapsuleRequest(
                    "标题", "内容含 ../etc/passwd", null,
                    LocalDateTime.now().plusDays(1), "Asia/Shanghai");

            assertThatThrownBy(() -> capsuleService.createCapsule(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            verify(timeCapsuleMapper, never()).insert(any(TimeCapsule.class));
        }
    }

    // ========== listMyCapsules ==========

    @Nested
    @DisplayName("listMyCapsules - 游标分页")
    class ListMyCapsulesTests {

        @Test
        @DisplayName("超出页大小：截断到 pageSize + 返回 nextCursor/hasMore")
        void listMyCapsules_hasMore() {
            List<TimeCapsule> rows = new ArrayList<>();
            for (long i = 30; i >= 10; i--) {
                TimeCapsule c = buildCapsule(CapsuleStatus.SEALED,
                        LocalDateTime.now().plusDays(i));
                c.setId(i);
                rows.add(c);
            }
            when(timeCapsuleMapper.selectList(any())).thenReturn(rows);

            var page = capsuleService.listMyCapsules(USER_ID, null, null, 20);

            assertThat(page.records()).hasSize(20);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isEqualTo("11");
            assertThat(page.records().get(19).id()).isEqualTo(11L);
            assertThat(page.records()).allSatisfy(vo -> {
                assertThat(vo.content()).isNull();
                assertThat(vo.mediaUrls()).isNull();
            });
        }

        @Test
        @DisplayName("OPENED 项返回内容，非 OPENED 项内容恒为 null")
        void listMyCapsules_contentVisibility() {
            TimeCapsule opened = buildCapsule(CapsuleStatus.OPENED,
                    LocalDateTime.now().minusDays(1));
            opened.setOpenedAt(LocalDateTime.now());
            TimeCapsule sealed = buildCapsule(CapsuleStatus.SEALED,
                    LocalDateTime.now().plusDays(1));
            sealed.setId(2002L);
            when(timeCapsuleMapper.selectList(any())).thenReturn(List.of(opened, sealed));

            var page = capsuleService.listMyCapsules(USER_ID, null, null, 20);

            assertThat(page.records().get(0).content()).isNotNull();
            assertThat(page.records().get(0).mediaUrls()).containsExactly("oss://a.png");
            assertThat(page.records().get(1).content()).isNull();
        }

        @Test
        @DisplayName("非法状态过滤值 / 非法游标 → WISH_VALIDATION_ERROR")
        void listMyCapsules_invalidParams_rejected() {
            assertThatThrownBy(() -> capsuleService.listMyCapsules(USER_ID, "BAD", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            assertThatThrownBy(() -> capsuleService.listMyCapsules(USER_ID, null, "abc", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    // ========== getCapsuleDetail / openCapsule / cancelCapsule ==========

    @Nested
    @DisplayName("getCapsuleDetail - 详情与归属")
    class GetDetailTests {

        @Test
        @DisplayName("非本人/不存在统一 404（防存在性探测）")
        void getDetail_notOwnerOrMissing_404() {
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(buildCapsule(CapsuleStatus.SEALED, LocalDateTime.now().plusDays(1)));

            assertThatThrownBy(() -> capsuleService.getCapsuleDetail(OTHER_USER_ID, CAPSULE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);

            when(timeCapsuleMapper.selectById(9999L)).thenReturn(null);
            assertThatThrownBy(() -> capsuleService.getCapsuleDetail(USER_ID, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }

        @Test
        @DisplayName("SEALED 详情内容返回 null（未到期不可见）")
        void getDetail_sealed_contentHidden() {
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(buildCapsule(CapsuleStatus.SEALED, LocalDateTime.now().plusDays(1)));

            var vo = capsuleService.getCapsuleDetail(USER_ID, CAPSULE_ID);

            assertThat(vo.status()).isEqualTo("SEALED");
            assertThat(vo.content()).isNull();
            assertThat(vo.mediaUrls()).isNull();
        }
    }

    @Nested
    @DisplayName("openCapsule - 到期开启")
    class OpenCapsuleTests {

        @Test
        @DisplayName("已开启幂等：直接返回内容，不发 CAS")
        void openCapsule_alreadyOpened_idempotent() {
            TimeCapsule opened = buildCapsule(CapsuleStatus.OPENED,
                    LocalDateTime.now().minusDays(1));
            opened.setOpenedAt(LocalDateTime.now().minusHours(1));
            when(timeCapsuleMapper.selectById(CAPSULE_ID)).thenReturn(opened);

            var vo = capsuleService.openCapsule(USER_ID, CAPSULE_ID);

            assertThat(vo.status()).isEqualTo("OPENED");
            assertThat(vo.content()).isNotNull();
            verify(timeCapsuleMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("已取消胶囊不可开启 → WISH_CAPSULE_NOT_AVAILABLE")
        void openCapsule_cancelled_rejected() {
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(buildCapsule(CapsuleStatus.CANCELLED,
                            LocalDateTime.now().minusDays(1)));

            assertThatThrownBy(() -> capsuleService.openCapsule(USER_ID, CAPSULE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CAPSULE_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("未到期 CAS 不命中 → WISH_CAPSULE_NOT_AVAILABLE(409)")
        void openCapsule_notExpired_rejected() {
            TimeCapsule sealed = buildCapsule(CapsuleStatus.SEALED,
                    LocalDateTime.now().plusDays(1));
            when(timeCapsuleMapper.selectById(CAPSULE_ID)).thenReturn(sealed);
            when(timeCapsuleMapper.update(isNull(), any())).thenReturn(0);

            assertThatThrownBy(() -> capsuleService.openCapsule(USER_ID, CAPSULE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CAPSULE_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("到期开启成功：CAS 命中 → OPENED 返回内容与 openedAt")
        void openCapsule_expired_success() {
            TimeCapsule available = buildCapsule(CapsuleStatus.AVAILABLE,
                    LocalDateTime.now().minusDays(1));
            TimeCapsule opened = buildCapsule(CapsuleStatus.OPENED,
                    LocalDateTime.now().minusDays(1));
            opened.setOpenedAt(LocalDateTime.now());
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(available, opened);
            when(timeCapsuleMapper.update(isNull(), any())).thenReturn(1);

            var vo = capsuleService.openCapsule(USER_ID, CAPSULE_ID);

            assertThat(vo.status()).isEqualTo("OPENED");
            assertThat(vo.content()).isNotNull();
            assertThat(vo.openedAt()).isNotNull();
        }

        @Test
        @DisplayName("并发双开：CAS 未命中但已到期 → 重查返回已开启内容（仅一次生效）")
        void openCapsule_concurrentLost_returnsOpenedContent() {
            TimeCapsule available = buildCapsule(CapsuleStatus.AVAILABLE,
                    LocalDateTime.now().minusDays(1));
            TimeCapsule openedByOther = buildCapsule(CapsuleStatus.OPENED,
                    LocalDateTime.now().minusDays(1));
            openedByOther.setOpenedAt(LocalDateTime.now());
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(available, openedByOther);
            when(timeCapsuleMapper.update(isNull(), any())).thenReturn(0);

            var vo = capsuleService.openCapsule(USER_ID, CAPSULE_ID);

            assertThat(vo.status()).isEqualTo("OPENED");
            assertThat(vo.content()).isNotNull();
        }
    }

    @Nested
    @DisplayName("cancelCapsule - 取消")
    class CancelCapsuleTests {

        @Test
        @DisplayName("已开启胶囊不可取消 → WISH_STATUS_CONFLICT")
        void cancelCapsule_opened_rejected() {
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(buildCapsule(CapsuleStatus.OPENED,
                            LocalDateTime.now().minusDays(1)));

            assertThatThrownBy(() -> capsuleService.cancelCapsule(USER_ID, CAPSULE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT);
        }

        @Test
        @DisplayName("已取消幂等：直接返回，不发 CAS")
        void cancelCapsule_cancelled_idempotent() {
            when(timeCapsuleMapper.selectById(CAPSULE_ID))
                    .thenReturn(buildCapsule(CapsuleStatus.CANCELLED,
                            LocalDateTime.now().plusDays(1)));

            var vo = capsuleService.cancelCapsule(USER_ID, CAPSULE_ID);

            assertThat(vo.status()).isEqualTo("CANCELLED");
            verify(timeCapsuleMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("SEALED 取消成功：CAS 命中 → CANCELLED")
        void cancelCapsule_sealed_success() {
            TimeCapsule sealed = buildCapsule(CapsuleStatus.SEALED,
                    LocalDateTime.now().plusDays(1));
            TimeCapsule cancelled = buildCapsule(CapsuleStatus.CANCELLED,
                    LocalDateTime.now().plusDays(1));
            when(timeCapsuleMapper.selectById(CAPSULE_ID)).thenReturn(sealed, cancelled);
            when(timeCapsuleMapper.update(isNull(), any())).thenReturn(1);

            var vo = capsuleService.cancelCapsule(USER_ID, CAPSULE_ID);

            assertThat(vo.status()).isEqualTo("CANCELLED");
            assertThat(vo.content()).as("取消后内容同样不可见").isNull();
        }
    }

    // ========== scanAvailableCapsules ==========

    @Nested
    @DisplayName("scanAvailableCapsules - 到期扫描")
    class ScanTests {

        @Test
        @DisplayName("无到期胶囊：0/0 且不发事件")
        void scan_empty() {
            when(timeCapsuleMapper.selectList(any())).thenReturn(List.of());

            var result = capsuleService.scanAvailableCapsules();

            assertThat(result.scanned()).isZero();
            assertThat(result.available()).isZero();
            verify(capsuleEventProducer, never())
                    .publishCapsuleAvailable(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("到期胶囊逐条 CAS 流转：仅对成功项发布通知（幂等推送）")
        void scan_publishesOnlyTransitioned() {
            TimeCapsule c1 = buildCapsule(CapsuleStatus.SEALED, LocalDateTime.now().minusDays(2));
            c1.setId(3001L);
            TimeCapsule c2 = buildCapsule(CapsuleStatus.SEALED, LocalDateTime.now().minusDays(1));
            c2.setId(3002L);
            when(timeCapsuleMapper.selectList(any())).thenReturn(List.of(c1, c2));
            // 第一条 CAS 命中、第二条被并发抢先（affected=0）
            when(timeCapsuleMapper.update(isNull(), any())).thenReturn(1, 0);

            var result = capsuleService.scanAvailableCapsules();

            assertThat(result.scanned()).isEqualTo(2);
            assertThat(result.available()).isEqualTo(1);
            verify(capsuleEventProducer, times(1))
                    .publishCapsuleAvailable(3001L, USER_ID, c1.getTitle());
            verify(capsuleEventProducer, never())
                    .publishCapsuleAvailable(3002L, USER_ID, c2.getTitle());
        }

        @Test
        @DisplayName("满批循环：500 条满批后再查一次直到取尽")
        void scan_fullBatchLoops() {
            List<TimeCapsule> fullBatch = new ArrayList<>();
            for (long i = 1; i <= CapsuleServiceImpl.SCAN_BATCH_SIZE; i++) {
                TimeCapsule c = buildCapsule(CapsuleStatus.SEALED,
                        LocalDateTime.now().minusHours(1));
                c.setId(i);
                fullBatch.add(c);
            }
            when(timeCapsuleMapper.selectList(any()))
                    .thenReturn(fullBatch, List.of());
            when(timeCapsuleMapper.update(isNull(), any())).thenReturn(1);

            var result = capsuleService.scanAvailableCapsules();

            assertThat(result.scanned()).isEqualTo(500);
            assertThat(result.available()).isEqualTo(500);
            verify(timeCapsuleMapper, times(2)).selectList(any());
            verify(capsuleEventProducer, times(500))
                    .publishCapsuleAvailable(anyLong(), anyLong(), any());
        }
    }

    // ========== reportTimezone ==========

    @Nested
    @DisplayName("reportTimezone - 时区上报")
    class ReportTimezoneTests {

        @Test
        @DisplayName("统计行存在：直接落库幂等")
        void reportTimezone_rowExists() {
            when(wishUserStatMapper.update(isNull(), any())).thenReturn(1);

            Map<String, Object> result = capsuleService.reportTimezone(
                    USER_ID, "America/New_York", -240);

            assertThat(result).containsEntry("timezone", "America/New_York")
                    .containsEntry("updated", true);
            verify(userStatService, never()).initUserStat(anyLong());
        }

        @Test
        @DisplayName("统计行不存在：initUserStat 后重试落库")
        void reportTimezone_rowMissing_initAndRetry() {
            when(wishUserStatMapper.update(isNull(), any())).thenReturn(0, 1);

            Map<String, Object> result = capsuleService.reportTimezone(
                    USER_ID, "Asia/Shanghai", 480);

            assertThat(result).containsEntry("updated", true);
            verify(userStatService).initUserStat(USER_ID);
            verify(wishUserStatMapper, times(2)).update(isNull(), any());
        }

        @Test
        @DisplayName("非法时区 → WISH_VALIDATION_ERROR 且不落库")
        void reportTimezone_invalidZone_rejected() {
            assertThatThrownBy(() -> capsuleService.reportTimezone(USER_ID, "Bad/Zone", 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            verify(wishUserStatMapper, never()).update(isNull(), any());
        }
    }

    // ========== getAdminStats ==========

    @Nested
    @DisplayName("getAdminStats - 管理统计")
    class AdminStatsTests {

        @Test
        @DisplayName("返回总量 + 四状态计数 + 今日创建")
        void adminStats_containsAllKeys() {
            when(timeCapsuleMapper.selectCount(any())).thenReturn(7L, 3L, 2L, 1L, 1L, 0L);

            Map<String, Object> stats = capsuleService.getAdminStats();

            assertThat(stats)
                    .containsEntry("total", 7L)
                    .containsEntry("sealed", 3L)
                    .containsEntry("available", 2L)
                    .containsEntry("opened", 1L)
                    .containsEntry("cancelled", 1L)
                    .containsEntry("todayCreated", 0L)
                    .hasSize(6);
        }
    }
}

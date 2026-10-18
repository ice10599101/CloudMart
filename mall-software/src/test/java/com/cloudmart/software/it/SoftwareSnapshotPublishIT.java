package com.cloudmart.software.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareSnapshotInput;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.service.SoftwarePublishService;
import com.cloudmart.software.vo.software.SoftwareEditorVO;
import com.cloudmart.software.vo.software.SoftwareSubmittedVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 快照发布链路集成测试（方案 6.3/9.7，A41 类回归）。
 *
 * <p>背景：MyBatis-Plus 默认 NOT_NULL 更新策略会跳过 null 列——曾导致两个线上级
 * 缺陷：① 审核通过后 working_revision_id 无法清空（已发布软件永远无法再编辑）；
 * ② 草稿保存"清空可空字段"静默失效并违反 DB 组合约束（REGISTERED + 残留
 * access_contact_id → chk_revision_access_combo 500）。本类以真实 MySQL 断言
 * 数据库终态，盖住 mock 单测盖不住的更新策略语义。</p>
 */
@DisplayName("快照发布集成测试（DB 终态断言）")
class SoftwareSnapshotPublishIT extends SoftwareIntegrationTestBase {

    private static final long OWNER = 8101L;
    private static final long ADMIN = 91L;

    @Autowired
    private SoftwarePublishService publishService;
    @Autowired
    private SoftwareMapper softwareMapper;
    @Autowired
    private SoftwareRevisionMapper revisionMapper;

    /** 开通发布资格（默认关闭，管理员逐用户开通） */
    private void seedPublisher(long userId) {
        jdbcTemplate.update("""
                INSERT INTO software_publisher_permission
                    (user_id, can_publish, updated_by, reason)
                VALUES (?, 1, ?, 'IT 开通')
                """, userId, ADMIN);
    }

    /** 种子一条启用的付款模式联系入口，返回其 ID */
    private long seedContact(String name) {
        jdbcTemplate.update("""
                INSERT INTO software_access_contact
                    (name, contact_type, contact_value, enabled, sort_order, updated_by)
                VALUES (?, 'HTTPS_URL', 'https://it.example.com/contact', 1, 10, ?)
                """, name, ADMIN);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM software_access_contact WHERE name = ?", Long.class, name);
    }

    /** 创建软件并按输入保存草稿（v1），返回编辑器视图 */
    private SoftwareEditorVO createAndSave(long userId, SoftwareSnapshotInput input) {
        var created = publishService.create(userId, "it-create-" + System.nanoTime());
        return publishService.saveDraft(userId, created.softwareId(), created.workingRevisionId(), input);
    }

    private SoftwareSnapshotInput baseInput() {
        SoftwareSnapshotInput input = new SoftwareSnapshotInput();
        input.setName("IT 快照工具");
        input.setSummary("集成测试用软件快照工具");
        input.setCategoryId(1L);
        input.setDescriptionHtml("<p>IT 集成测试。</p>");
        input.setLicenseType("FREE");
        input.setAttributionType("ORIGINAL");
        input.setAccessMode("REGISTERED");
        input.setReleases(List.of(release("1.0.0", "首发")));
        return input;
    }

    private SoftwareSnapshotInput.ReleaseInput release(String version, String changelog) {
        SoftwareSnapshotInput.ReleaseInput release = new SoftwareSnapshotInput.ReleaseInput();
        release.setVersionName(version);
        release.setChannel("STABLE");
        release.setRecommended(true);
        release.setChangelog(changelog);
        SoftwareSnapshotInput.SourceInput source = new SoftwareSnapshotInput.SourceInput();
        source.setSourceType("NETDISK");
        source.setPlatform("WINDOWS");
        source.setArch("X64");
        source.setLabel("IT 网盘");
        source.setUrl("https://pan.baidu.com/s/1itabc?pwd=it01");
        source.setExtractionCode("itsecret");
        release.setSources(List.of(source));
        return release;
    }

    @Test
    @DisplayName("查看条件切换清空互斥列：ADMIN_PAID→REGISTERED 落库后 access_contact_id 必须为 NULL（A41 回归）")
    void saveDraft_accessSwitchPersistsNull() {
        seedPublisher(OWNER);
        long contactId = seedContact("IT-客服-" + System.nanoTime());

        // v1：REGISTERED
        SoftwareEditorVO v1 = createAndSave(OWNER, baseInput());
        long sid = v1.softwareId();
        long rid = v1.revisionId();

        // v2：切到 ADMIN_PAID（携带联系入口）
        SoftwareSnapshotInput paid = baseInput();
        paid.setAccessMode("ADMIN_PAID");
        paid.setAccessContactId(contactId);
        publishService.saveDraft(OWNER, sid, rid, paid);
        SoftwareRevision afterPaid = revisionMapper.selectById(rid);
        assertThat(afterPaid.getAccessMode().name()).isEqualTo("ADMIN_PAID");
        assertThat(afterPaid.getAccessContactId()).isEqualTo(contactId);

        // v3：切回 REGISTERED——互斥列必须真实写 NULL（NOT_NULL 策略会静默跳过，
        // 留下 ADMIN_PAID 残留 → UPDATE 触发 chk_revision_access_combo 500）
        SoftwareSnapshotInput back = baseInput();
        back.setAccessMode("REGISTERED");
        back.setAccessContactId(null);
        SoftwareEditorVO v3 = publishService.saveDraft(OWNER, sid, rid, back);

        SoftwareRevision afterBack = revisionMapper.selectById(rid);
        assertThat(afterBack.getAccessMode().name()).isEqualTo("REGISTERED");
        assertThat(afterBack.getAccessContactId()).as("access_contact_id 应被清空").isNull();
        assertThat(afterBack.getMinCommunityLevel()).as("min_community_level 应为 NULL").isNull();
    }

    @Test
    @DisplayName("清空其他可空字段同样持久化：official_url/developer_name 置空生效")
    void saveDraft_clearingOptionalScalarsPersists() {
        seedPublisher(OWNER);
        SoftwareSnapshotInput input = baseInput();
        input.setOfficialUrl("https://it.example.com");
        input.setDeveloperName("IT 开发者");
        SoftwareEditorVO v1 = createAndSave(OWNER, input);

        SoftwareSnapshotInput cleared = baseInput();
        cleared.setOfficialUrl(null);
        cleared.setDeveloperName(null);
        publishService.saveDraft(OWNER, v1.softwareId(), v1.revisionId(), cleared);

        SoftwareRevision after = revisionMapper.selectById(v1.revisionId());
        assertThat(after.getOfficialUrl()).as("official_url 应被清空").isNull();
        assertThat(after.getDeveloperName()).as("developer_name 应被清空").isNull();
    }

    @Test
    @DisplayName("提交→撤回→重提：审核通过切换 published 并清空 working 指针（无版本参数）")
    void publishFlow_submitWithdrawApprove_switchesPointerAndClearsWorking() {
        seedPublisher(OWNER);
        SoftwareEditorVO v1 = createAndSave(OWNER, baseInput());
        long sid = v1.softwareId();
        long rid = v1.revisionId();

        // 提交 seq1
        SoftwareSubmittedVO submitted = publishService.submit(OWNER, sid, rid, "it-submit-1");
        assertThat(submitted.reviewStatus()).isEqualTo("PENDING");

        // SW04：撤回不原地改旧提交，而是生成内容相同的新 DRAFT
        SoftwareEditorVO withdrawn = publishService.withdraw(OWNER, sid, rid);
        long draftId = withdrawn.revisionId();
        assertThat(draftId).isNotEqualTo(rid);
        assertThat(revisionMapper.selectById(rid).getReviewStatus())
                .isEqualTo(SoftwareReviewStatus.WITHDRAWN);
        assertThat(revisionMapper.selectById(draftId).getReviewStatus())
                .isEqualTo(SoftwareReviewStatus.DRAFT);

        // 重提后审核：published 指向新快照、working 指针清空（写 NULL 必须落库）
        SoftwareSnapshotInput v3 = baseInput();
        v3.setName("IT 快照工具三版");
        publishService.saveDraft(OWNER, sid, draftId, v3);
        SoftwareSubmittedVO resubmitted = publishService.submit(OWNER, sid, draftId, "it-submit-2");
        assertThat(resubmitted.reviewStatus()).isEqualTo("PENDING");

        publishService.reviewDecision(ADMIN, "it-decide-ok", sid, draftId,
                true, "通过", "it-req-2");

        Software after = softwareMapper.selectById(sid);
        assertThat(after.getPublishedRevisionId()).isEqualTo(draftId);
        assertThat(after.getWorkingRevisionId()).as("审核通过后 working 指针必须清空").isNull();
        assertThat(after.getLifecycle().name()).isEqualTo("ACTIVE");
        assertThat(after.getPublicationSeq()).isEqualTo(1);

        // 已发布后可再次克隆出可编辑草稿（旧代码 working 滞留会直接返回 APPROVED 快照）
        SoftwareEditorVO next = publishService.workingRevision(OWNER, sid, "it-clone-1");
        assertThat(next.revisionId()).isNotEqualTo(rid);
        assertThat(revisionMapper.selectById(next.revisionId()).getReviewStatus())
                .isEqualTo(SoftwareReviewStatus.DRAFT);
    }
}

package com.cloudmart.pet.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 迁移契约 IT（R26/§16.2）：真实 MySQL 容器上执行全部 Flyway 迁移，
 * 覆盖单元测试无法触及的数据库语义——生成列 open_key 的 PENDING 唯一性、
 * nullable 唯一键的 NULL 不相等语义、各事实表 uk 幂等约束。
 *
 * <p>无 Docker 环境自动跳过（{@code disabledWithoutDocker = true}）；
 * CI 有 Docker 时随 test 阶段执行。迁移为一次性全量应用（容器即全新库）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("migration-it")
@DisplayName("宠物迁移契约（真实 MySQL 容器）")
class PetMigrationContractTest {

    @Container
    private final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.0");

    private Connection connection;

    @BeforeAll
    void migrateSchema() throws SQLException {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    @Test
    @DisplayName("全部迁移在全新库一次性应用成功（无失败行，头版本对齐当前最新）")
    void allMigrationsApplied() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet failed = st.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0");
             ResultSet head = st.executeQuery(
                     "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history")) {
            failed.next();
            assertThat(failed.getInt(1)).as("失败的迁移数").isZero();
            head.next();
            assertThat(head.getInt(1)).as("头迁移版本").isEqualTo(67);
        }
    }

    @Test
    @DisplayName("V61 举报 open_key：同用户同目标至多一张 PENDING，结案后可再次举报")
    void reportOpenKeyEnforcesSinglePending() throws SQLException {
        insertReport(101L, 1L, "WALL_MESSAGE", 900L);
        // 第二张同键 PENDING → uk_pet_report_open 拒绝
        assertThatThrownBy(() -> insertReport(102L, 1L, "WALL_MESSAGE", 900L))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Duplicate entry");
        // 结案后生成列置 NULL 退出唯一约束：可再次举报同一目标
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pet_report SET status = 'REJECTED' WHERE id = 101")) {
            ps.executeUpdate();
        }
        insertReport(103L, 1L, "WALL_MESSAGE", 900L);
    }

    @Test
    @DisplayName("V61 nullable 语义：非 PENDING 历史行 open_key=NULL 不参与唯一（§16.2 陷阱）")
    void nullOpenKeysDoNotCollide() throws SQLException {
        // 多条 HANDLED（open_key=NULL）同键共存
        insertReport(111L, 2L, "NICKNAME", 800L, "HANDLED");
        insertReport(112L, 2L, "NICKNAME", 800L, "HANDLED");
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM pet_report WHERE reporter_user_id = 2 AND status = 'HANDLED'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("V65 任务回执：uk(quest_code, event_id) 同事实至多消费一次")
    void questReceiptUnique() throws SQLException {
        insertReceipt(201L, "WORK", "ACT_CLAIM:55");
        assertThatThrownBy(() -> insertReceipt(202L, "WORK", "ACT_CLAIM:55"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Duplicate entry");
        // 不同任务类型可用同一事实键（职业领取：WORK 与 CAREER_WORK 各持一条）
        insertReceipt(203L, "CAREER_WORK", "ACT_CLAIM:55");
    }

    @Test
    @DisplayName("V62 合作成员：uk(user_id, week_start) 同周至多一队（跨角色关闭组合缺口）")
    void cooperationMemberUniquePerWeek() throws SQLException {
        LocalDate monday = LocalDate.parse("2026-10-05");
        insertMember(301L, 30L, monday, 60L, "INVITER");
        // 同用户同周第二支队伍（即使角色不同）→ 拒绝
        assertThatThrownBy(() -> insertMember(302L, 30L, monday, 61L, "INVITEE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Duplicate entry");
        // 下周可再参与
        insertMember(303L, 30L, monday.plusWeeks(1), 62L, "INVITEE");
    }

    @Test
    @DisplayName("V62 陪伴分账：uk(pet_id, business_date) 单宠单日一行")
    void companionDailyPetUnique() throws SQLException {
        insertCompanionLedger(401L, 40L, LocalDate.parse("2026-10-05"));
        assertThatThrownBy(() -> insertCompanionLedger(402L, 40L, LocalDate.parse("2026-10-05")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Duplicate entry");
    }

    @Test
    @DisplayName("V64 期次领奖事实：uk(occurrence_id, pet_id) 同宠同期至多领一次")
    void occurrenceClaimUnique() throws SQLException {
        insertOccurrenceClaim(501L, 500L, 50L);
        assertThatThrownBy(() -> insertOccurrenceClaim(502L, 500L, 50L))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Duplicate entry");
    }

    // ---------------- 数据写入辅助 ----------------

    private void insertReport(long id, long reporter, String targetType, long targetId) throws SQLException {
        insertReport(id, reporter, targetType, targetId, "PENDING");
    }

    private void insertReport(long id, long reporter, String targetType, long targetId, String status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pet_report (id, reporter_user_id, target_type, target_id, reason, status, is_auto, report_date) "
                        + "VALUES (?, ?, ?, ?, 'IT验证', ?, 0, CURDATE())")) {
            ps.setLong(1, id);
            ps.setLong(2, reporter);
            ps.setString(3, targetType);
            ps.setLong(4, targetId);
            ps.setString(5, status);
            ps.executeUpdate();
        }
    }

    private void insertReceipt(long id, String questCode, String eventId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pet_quest_event_receipt (id, user_id, pet_id, quest_code, event_id, source_time, business_date, amount) "
                        + "VALUES (?, 1, 1, ?, ?, UTC_TIMESTAMP(), CURDATE(), 1)")) {
            ps.setLong(1, id);
            ps.setString(2, questCode);
            ps.setString(3, eventId);
            ps.executeUpdate();
        }
    }

    private void insertMember(long id, long userId, LocalDate weekStart, long cooperationId, String role) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pet_cooperation_member (id, user_id, week_start, cooperation_id, role, pet_id) "
                        + "VALUES (?, ?, ?, ?, ?, 1)")) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.setObject(3, weekStart);
            ps.setLong(4, cooperationId);
            ps.setString(5, role);
            ps.executeUpdate();
        }
    }

    private void insertCompanionLedger(long id, long petId, LocalDate businessDate) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pet_companion_daily_pet (id, user_id, pet_id, business_date, accepted_seconds, granted_minutes, qualified_day) "
                        + "VALUES (?, 1, ?, ?, 60, 1, 1)")) {
            ps.setLong(1, id);
            ps.setLong(2, petId);
            ps.setObject(3, businessDate);
            ps.executeUpdate();
        }
    }

    private void insertOccurrenceClaim(long id, long occurrenceId, long petId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pet_event_occurrence_claim (id, occurrence_id, event_code, pet_id, user_id) "
                        + "VALUES (?, ?, 'IT_EVENT', ?, 1)")) {
            ps.setLong(1, id);
            ps.setLong(2, occurrenceId);
            ps.setLong(3, petId);
            ps.executeUpdate();
        }
    }
}

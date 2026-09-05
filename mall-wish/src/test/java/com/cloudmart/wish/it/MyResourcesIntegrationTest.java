package com.cloudmart.wish.it;

import com.cloudmart.wish.enums.ResourceLogType;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.MyResourcesVO;
import com.cloudmart.wish.vo.ResourceLogVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 个人星光资源集成测试（文档 L848：GET /wish/my/resources + /logs）。
 *
 * <p>覆盖：余额快照读取、今日收支聚合（EARN 正 / SPEND 负取绝对值）、
 * 无记录用户零值兜底、流水分页 cursor / type 过滤、reason 中文映射。
 * 流水写入走 earn/spend 的链路已由还愿/兑换等既有测试覆盖，此处直插
 * 流水行以聚焦本次新增的读路径聚合逻辑。</p>
 */
@DisplayName("我的星光资源集成测试")
class MyResourcesIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private UserStatService userStatService;

    private static final Long USER_ID = 3301L;

    /** 直插 4 条流水（id 递增 = 时间正序）：EARN +50/+3，SPEND -2/-5 */
    private void seedResourceLogs() {
        seedUserStat(USER_ID, 146);
        Object[][] rows = {
                { 9001L, 50, "EARN", "SIGNIN", 150 },
                { 9002L, 3, "EARN", "CHECKIN", 153 },
                { 9003L, -2, "SPEND", "LIGHT_OTHER", 151 },
                { 9004L, -5, "SPEND", "ANON_STAR", 146 },
        };
        for (Object[] row : rows) {
            jdbcTemplate.update(
                    "INSERT INTO wish_resource_log (id, user_id, delta, type, source, balance_after, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    row[0], USER_ID, row[1], row[2], row[3], row[4]);
        }
    }

    @Test
    @DisplayName("余额概览：快照余额 + 今日 EARN 求和 / SPEND 绝对值求和")
    void getMyResources_aggregatesToday() {
        seedResourceLogs();

        MyResourcesVO vo = userStatService.getMyResources(USER_ID);

        assertThat(vo.balance()).isEqualTo(146);
        assertThat(vo.todayEarned()).isEqualTo(53);
        assertThat(vo.todaySpent()).isEqualTo(7);
    }

    @Test
    @DisplayName("余额概览：无统计记录的用户 → 全 0 兜底（不抛错）")
    void getMyResources_noStat_zeroFallback() {
        MyResourcesVO vo = userStatService.getMyResources(USER_ID);

        assertThat(vo.balance()).isZero();
        assertThat(vo.todayEarned()).isZero();
        assertThat(vo.todaySpent()).isZero();
    }

    @Test
    @DisplayName("流水：默认按时间倒序，amount 恒正 + reason 中文映射")
    void listResourceLogs_descWithLabels() {
        seedResourceLogs();

        List<ResourceLogVO> logs = userStatService.listResourceLogs(USER_ID, null, null, 20);

        assertThat(logs).hasSize(4);
        // id 倒序（雪花 ID 恒等时序倒序）
        assertThat(logs.get(0).id()).isEqualTo(9004L);
        assertThat(logs.get(3).id()).isEqualTo(9001L);
        // amount 恒为正 + 方向由 type 表达
        assertThat(logs.stream().filter(l -> "SPEND".equals(l.type())).map(ResourceLogVO::amount))
                .containsOnly(2, 5);
        // reason 中文映射
        assertThat(logs.stream().map(ResourceLogVO::reason))
                .contains("每日签到", "心愿打卡", "点亮他人", "匿名星光");
        // balanceAfter 快照存在
        assertThat(logs).allSatisfy(l -> assertThat(l.balanceAfter()).isNotNull());
    }

    @Test
    @DisplayName("流水：type=EARN 过滤 + cursor 分页")
    void listResourceLogs_typeFilterAndCursor() {
        seedResourceLogs();

        List<ResourceLogVO> earns = userStatService.listResourceLogs(USER_ID, ResourceLogType.EARN, null, 20);
        assertThat(earns).hasSize(2);
        assertThat(earns).allSatisfy(l -> assertThat(l.type()).isEqualTo("EARN"));

        // 第一页 3 条，游标为末条（9002）；第二页仅剩 9001
        List<ResourceLogVO> page1 = userStatService.listResourceLogs(USER_ID, null, null, 3);
        assertThat(page1).hasSize(3);
        List<ResourceLogVO> page2 = userStatService.listResourceLogs(USER_ID, null, page1.get(2).id(), 20);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).id()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("流水：pageSize 越界（999）收敛为 50，不抛错")
    void listResourceLogs_pageSizeClamped() {
        seedResourceLogs();

        List<ResourceLogVO> logs = userStatService.listResourceLogs(USER_ID, null, null, 999);
        assertThat(logs).hasSize(4);
    }
}

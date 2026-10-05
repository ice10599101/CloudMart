-- 宠物模块开发期数据清零脚本（原 V29__pet_dev_data_reset.sql，PET-01 已迁出 Flyway 自动迁移生命周期）
-- 背景：模块处于开发阶段、无真实用户数据；水果化改版后，旧物种及其关联的用户侧数据
-- 一律清零，只保留"配置/目录"表（商城在售、成就定义、任务/职业/事件配置等种子数据）。
--
-- ⚠️ 本脚本含多表 TRUNCATE，禁止作为 Flyway 迁移自动执行：
--   1. 只允许人工在确认目标库为开发库后手动执行（见下方守卫与执行方式）；
--   2. 有真实数据的环境必须先完成备份与行数核对（执行清单见同目录 README.md）；
--   3. flyway_schema_history 中已记录 V29 的环境由 spring.flyway.ignore-migration-patterns=29:missing
--      豁免"已应用迁移本地缺失"校验；未执行过 V29 的环境不再补执行本脚本。
--
-- 保留名单（13 张，新增配置表时必须同步加入本名单再跑清零）：
--   pet_job_config / pet_study_config / pet_skill_config / pet_equipment_config /
--   pet_furniture_config / pet_skin_config / pet_career_config / pet_daily_quest_config /
--   pet_event_config / pet_evolution_config
--   pet_achievement（成就定义目录） / pet_collection_entry（图鉴目录）
--   pet_config_version（配置版本快照，回退依据）
--
-- 执行方式（mysql CLI）：
--   mysql -h <host> -P <port> -u <user> -p <dev_database> < pet_dev_data_reset.sql
-- 守卫通过 mysql CLI 的 DELIMITER 语法实现；不支持 DELIMITER 的图形工具请手动执行守卫段。

-- ---- 环境守卫：仅允许在开发库执行，其余库名直接报错终止，不触碰任何业务表 ----
DROP PROCEDURE IF EXISTS pet_dev_reset_guard;
DELIMITER $$
CREATE PROCEDURE pet_dev_reset_guard()
BEGIN
    -- 库名白名单：cloudmart_dev / mall_pet_dev（新增开发库名必须同步修改此处）
    IF DATABASE() IS NULL OR DATABASE() NOT REGEXP '^(cloudmart_dev|mall_pet_dev)$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'PET_DEV_RESET_FORBIDDEN: 本脚本仅允许在 cloudmart_dev/mall_pet_dev 开发库执行';
    END IF;
END$$
DELIMITER ;
CALL pet_dev_reset_guard();
DROP PROCEDURE pet_dev_reset_guard;

-- ---- 用户/运行时数据：全部清零（TRUNCATE 隐式提交、可重复执行） ----
TRUNCATE TABLE `pet`;
TRUNCATE TABLE `pet_achievement_record`;
TRUNCATE TABLE `pet_activity`;
TRUNCATE TABLE `pet_album_asset`;
TRUNCATE TABLE `pet_asset_grant`;
TRUNCATE TABLE `pet_battle`;
TRUNCATE TABLE `pet_bottle_record`;
TRUNCATE TABLE `pet_career_progress`;
TRUNCATE TABLE `pet_career_stint`;
TRUNCATE TABLE `pet_chat_message`;
TRUNCATE TABLE `pet_chat_session`;
TRUNCATE TABLE `pet_collection_record`;
TRUNCATE TABLE `pet_companion_daily`;
TRUNCATE TABLE `pet_companion_session`;
TRUNCATE TABLE `pet_context_counter`;
TRUNCATE TABLE `pet_cooperation`;
TRUNCATE TABLE `pet_cooperation_contribution`;
TRUNCATE TABLE `pet_custody_record`;
TRUNCATE TABLE `pet_daily_quest`;
TRUNCATE TABLE `pet_daily_quota`;
TRUNCATE TABLE `pet_diary_entry`;
TRUNCATE TABLE `pet_event_progress`;
TRUNCATE TABLE `pet_friend`;
TRUNCATE TABLE `pet_inventory`;
TRUNCATE TABLE `pet_memory`;
TRUNCATE TABLE `pet_migration_conflict`;
TRUNCATE TABLE `pet_minigame_round`;
TRUNCATE TABLE `pet_notify_pref`;
TRUNCATE TABLE `pet_offline_cursor`;
TRUNCATE TABLE `pet_onboarding_progress`;
TRUNCATE TABLE `pet_operation`;
TRUNCATE TABLE `pet_outbox_event`;
TRUNCATE TABLE `pet_purchase_order`;
TRUNCATE TABLE `pet_relation`;
TRUNCATE TABLE `pet_report`;
TRUNCATE TABLE `pet_request_dedup`;
TRUNCATE TABLE `pet_reward_claim`;
TRUNCATE TABLE `pet_room`;
TRUNCATE TABLE `pet_room_item`;
TRUNCATE TABLE `pet_room_like`;
TRUNCATE TABLE `pet_skill`;
TRUNCATE TABLE `pet_user_block`;
TRUNCATE TABLE `pet_user_guard`;
TRUNCATE TABLE `pet_visit_fact`;
TRUNCATE TABLE `pet_wall_like`;
TRUNCATE TABLE `pet_wall_message`;
TRUNCATE TABLE `pet_wallet_account`;
TRUNCATE TABLE `pet_wallet_adjustment`;
TRUNCATE TABLE `pet_wallet_cutover_batch`;
TRUNCATE TABLE `pet_wallet_cutover_item`;
TRUNCATE TABLE `pet_wallet_ledger`;
TRUNCATE TABLE `pet_wallet_reconcile_item`;
TRUNCATE TABLE `pet_wallet_reconcile_run`;
TRUNCATE TABLE `pet_wallet_transaction`;

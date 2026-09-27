-- V29: 宠物模块开发期数据清零（2026-09-28）
-- 背景：模块处于开发阶段、无真实用户数据；水果化改版后，旧物种及其关联的用户侧数据
-- 一律清零，只保留"配置/目录"表（商城在售、成就定义、任务/职业/事件配置等种子数据）。
--
-- 保留名单（13 张，新增配置表时必须同步加入本名单再跑清零）：
--   pet_job_config / pet_study_config / pet_skill_config / pet_equipment_config /
--   pet_furniture_config / pet_skin_config / pet_career_config / pet_daily_quest_config /
--   pet_event_config / pet_evolution_config
--   pet_achievement（成就定义目录） / pet_collection_entry（图鉴目录）
--   pet_config_version（配置版本快照，回退依据）
--
-- 说明：TRUNCATE 隐式提交、可重复执行；本迁移与 V28（species 枚举水果化）配合使用。

-- ---- 用户/运行时数据：全部清零 ----
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

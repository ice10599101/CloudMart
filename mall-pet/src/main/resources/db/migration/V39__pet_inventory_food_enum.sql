-- F1 修正：pet_inventory.item_type ENUM 扩展 FOOD（漏于 V37——插入 FOOD 行被数据库截断拒绝，
-- 表现为购买 500 且事务回滚）。本地单测 mock 了 mapper 层未暴露，远程联调验收发现。

ALTER TABLE `pet_inventory`
    MODIFY COLUMN `item_type`
        ENUM('EQUIPMENT','SKIN','SKILL_BOOK','FURNITURE','FOOD') NOT NULL
        COMMENT '物品类型:装备/皮肤/技能书/家具/食物';

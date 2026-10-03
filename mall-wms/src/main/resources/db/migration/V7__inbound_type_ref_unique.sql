-- T11 切片三：退货入库幂等——同类型同关联单号只允许一张入库单
-- （RETURN + 案件号由 AfterSaleReturnListener 自动建单，先查后建之外
--   以 DB 唯一键兜底并发双投递；PURCHASE/TRANSFER 的关联单号语义同为
--   "一个来源单一张入库单"）。reference_no 允许 NULL（无来源单的手工单），
--   MySQL 唯一键对 NULL 不去重，不受影响。
-- 若存量数据已存在 (type, reference_no) 重复组，迁移失败即暴露数据问题，
-- 需人工合并后重放；远程/CI 环境该表当前无重复组（已核实）。
ALTER TABLE `inbound_orders`
    ADD UNIQUE KEY `uk_inbound_orders_type_ref` (`type`, `reference_no`);

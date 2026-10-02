-- T11 切片二 B：退货物流字段——RETURN_REFUND 案件受理后用户寄回商品，
-- 登记运单（承运商+单号唯一，防重复登记）；质检结果首期人工录入留痕。

ALTER TABLE after_sale_case
    ADD COLUMN return_carrier VARCHAR(32)  NULL COMMENT '退货承运商' AFTER handled_at,
    ADD COLUMN return_tracking_no VARCHAR(40) NULL COMMENT '退货运单号' AFTER return_carrier,
    ADD COLUMN return_shipped_at DATETIME(3) NULL COMMENT '退货寄出时间' AFTER return_tracking_no,
    ADD COLUMN inspect_result VARCHAR(20) NULL COMMENT '质检结果：PASSED-通过/REJECTED-不通过（人工录入）' AFTER return_shipped_at,
    ADD COLUMN inspect_note VARCHAR(500) NULL COMMENT '质检备注' AFTER inspect_result,
    ADD COLUMN inspected_at DATETIME(3) NULL COMMENT '质检时间' AFTER inspect_note,
    ADD UNIQUE KEY uk_after_sale_return_tracking (return_carrier, return_tracking_no);

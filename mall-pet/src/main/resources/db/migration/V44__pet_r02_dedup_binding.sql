-- V44 (R02): 请求去重扩展——旧客户端无显式 petId 的首次绑定冻结 + 终态元数据
-- 方案 9.1 请求去重（扩展）：新增绑定 petId、terminalErrorCode、finishedAt；
-- 复用既有 payloadHash/leaseOwner/version/leaseUntil/responseJson。
-- 可空列，旧程序仍可读写（expand 阶段，不收紧非空）。

ALTER TABLE `pet_request_dedup`
    ADD COLUMN `bound_pet_id` BIGINT UNSIGNED NULL
        COMMENT '首次执行绑定的目标宠物ID（旧请求无显式petId时冻结，重放按原归属）' AFTER `payload_hash`,
    ADD COLUMN `terminal_error_code` VARCHAR(64) NULL
        COMMENT '业务拒绝终态码（COMPLETED 且为拒绝结果时记录，重放返回同一拒绝）' AFTER `response_json`,
    ADD COLUMN `finished_at` DATETIME NULL
        COMMENT '终态完成时间（UTC）' AFTER `terminal_error_code`;

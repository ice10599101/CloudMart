-- T11 切片一：售后案件事实（after_sale_case）
-- 与 T02 refund_order 分工：refund_order 是资金退款单（mall-payment 权威），
-- after_sale_case 是用户售后案件（原因/附件/数量/时间线/受理结论，mall-order 权威）。
-- 案件状态机：PENDING → APPROVED（走 T02 退款）/ REJECTED → REFUNDED（退款完成回填）/ CLOSED。
-- 未发货全额退款与已发货退货退款首期同走本表，部分退款（按行）预留 item_id 可空=整单。

CREATE TABLE after_sale_case (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no         VARCHAR(40)      NOT NULL COMMENT '案件号 ASC 前缀',
    order_id        BIGINT UNSIGNED  NOT NULL COMMENT '订单ID',
    user_id         BIGINT UNSIGNED  NOT NULL COMMENT '申请人用户ID',
    item_id         BIGINT UNSIGNED  NULL COMMENT '订单项ID（NULL=整单售后）',
    type            VARCHAR(20)      NOT NULL COMMENT '类型：REFUND_ONLY-仅退款 / RETURN_REFUND-退货退款',
    reason          VARCHAR(500)     NOT NULL COMMENT '申请原因（用户填写）',
    attachment_file_ids VARCHAR(512) NULL COMMENT '附件文件ID列表（JSON 数组，S01 资产 ID）',
    quantity        INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '售后数量（整单=0 表示全部）',
    status          VARCHAR(20)      NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待受理/APPROVED-已受理(退款中)/REJECTED-已拒绝/REFUNDED-退款完成/CLOSED-已关闭',
    refund_no       VARCHAR(40)      NULL COMMENT '关联退款单号（T02 refund_order.refund_no）',
    refund_amount   DECIMAL(10,2)    NULL COMMENT '批准退款金额（受理时冻结）',
    reject_reason   VARCHAR(500)     NULL COMMENT '拒绝原因（运营填写）',
    handled_by      BIGINT UNSIGNED  NULL COMMENT '受理管理员ID',
    handled_at      DATETIME(3)      NULL COMMENT '受理/拒绝时间',
    created_at      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_case_no (case_no),
    KEY idx_after_sale_order (order_id),
    KEY idx_after_sale_user (user_id, status),
    KEY idx_after_sale_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '售后案件（T11）';

CREATE TABLE after_sale_case_event (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_id     BIGINT UNSIGNED NOT NULL COMMENT '案件ID',
    action      VARCHAR(32)     NOT NULL COMMENT '动作：APPLY/APPROVE/REJECT/REFUND_COMPLETED/CLOSED',
    operator    VARCHAR(64)     NULL COMMENT '操作者（user:{id} / admin:{id} / system）',
    detail      VARCHAR(1000)   NULL COMMENT '详情（JSON：金额/原因/单号等）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_case_event_case (case_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '售后案件时间线（T11）';

-- 修复：software_access_grant.confirmation_note 为 NOT NULL 且无默认值，
-- MyBatis-Plus 在字段为 null 时省略该列（insertStrategy 默认 NOT_NULL），
-- 触发 MySQL "Field 'confirmation_note' doesn't have a default value"，
-- 使「开通/延期付款授权」接口整体 500。
-- 这里按 AGENTS.md 13.3 要求以新增迁移修复，不修改已提交的 V3。
-- 备注允许缺省（多数授权无线下核对备注），但保持 NOT NULL 语义，缺省落空串。
ALTER TABLE `software_access_grant`
  MODIFY `confirmation_note` varchar(500) NOT NULL DEFAULT '' COMMENT '管理员人工确认备注（线下付款核对；仅有权管理员可见；缺省空串）';

-- SF01 部署验收缺陷修复：来源类型/平台/架构 CHECK 约束与枚举对齐。
-- 背景：SF01 新增 HTTPS_DOWNLOAD/OFFICIAL_PAGE/WEB_APP 来源类型、WEB 平台、ANY 架构，
-- 但未同步 software_source 的 CHECK 约束。单测环境无真实 MySQL CHECK 校验
-- （Testcontainers IT 本机不可用），远程部署后首次写入 HTTPS_DOWNLOAD 来源
-- 触发 chk_source_type 违反（UncategorizedSQLException 500）。
-- 修复：按枚举全集重建三个表级 CHECK 约束（MySQL 9 不支持 ALTER CHECK，只能 DROP+ADD）。

ALTER TABLE `software_source` DROP CHECK `chk_source_type`;
ALTER TABLE `software_source`
    ADD CONSTRAINT `chk_source_type` CHECK (`source_type` IN ('NETDISK','MAGNET','TORRENT_URL','HTTPS_DOWNLOAD','OFFICIAL_PAGE','WEB_APP'));

ALTER TABLE `software_source` DROP CHECK `chk_source_platform`;
ALTER TABLE `software_source`
    ADD CONSTRAINT `chk_source_platform` CHECK (`platform` IN ('WINDOWS','MACOS','LINUX','ANDROID','IOS','CROSS_PLATFORM','WEB'));

ALTER TABLE `software_source` DROP CHECK `chk_source_arch`;
ALTER TABLE `software_source`
    ADD CONSTRAINT `chk_source_arch` CHECK (`arch` IN ('X64','X86','ARM64','UNIVERSAL','OTHER','ANY'));

-- CloudMart mall-admin 初始 Schema 基线

CREATE TABLE IF NOT EXISTS `admin_config` (
  `id` bigint unsigned NOT NULL COMMENT '参数ID(雪花算法)',
  `config_name` varchar(128) NOT NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(128) NOT NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) NOT NULL DEFAULT '' COMMENT '参数键值',
  `config_type` tinyint NOT NULL DEFAULT '0' COMMENT '系统内置: 0-否, 1-是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='参数配置表';

CREATE TABLE IF NOT EXISTS `admin_dept` (
  `id` bigint unsigned NOT NULL COMMENT '部门ID(雪花算法)',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父部门ID',
  `ancestors` varchar(255) NOT NULL DEFAULT '' COMMENT '祖级列表(逗号分隔)',
  `dept_name` varchar(64) NOT NULL COMMENT '部门名称',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(64) DEFAULT NULL COMMENT '负责人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

CREATE TABLE IF NOT EXISTS `admin_dict_data` (
  `id` bigint unsigned NOT NULL COMMENT '字典数据ID(雪花算法)',
  `dict_type` varchar(128) NOT NULL DEFAULT '' COMMENT '字典类型',
  `dict_sort` int NOT NULL DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(128) NOT NULL DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(128) NOT NULL DEFAULT '' COMMENT '字典键值',
  `css_class` varchar(128) DEFAULT NULL COMMENT '样式属性',
  `list_class` varchar(128) DEFAULT NULL COMMENT '表格回显样式',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认: 0-否, 1-是',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS `admin_dict_type` (
  `id` bigint unsigned NOT NULL COMMENT '字典类型ID(雪花算法)',
  `dict_name` varchar(128) NOT NULL DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(128) NOT NULL DEFAULT '' COMMENT '字典类型',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `admin_login_log` (
  `id` bigint unsigned NOT NULL COMMENT '访问ID(雪花算法)',
  `username` varchar(64) NOT NULL DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) NOT NULL DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(256) NOT NULL DEFAULT '' COMMENT '登录地点',
  `browser` varchar(64) NOT NULL DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(64) NOT NULL DEFAULT '' COMMENT '操作系统',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '登录状态: 0-成功, 1-失败',
  `msg` varchar(256) NOT NULL DEFAULT '' COMMENT '提示信息',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志记录表';

CREATE TABLE IF NOT EXISTS `admin_menu` (
  `id` bigint unsigned NOT NULL COMMENT '菜单ID(雪花算法)',
  `menu_name` varchar(64) NOT NULL COMMENT '菜单名称',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(256) NOT NULL DEFAULT '' COMMENT '路由地址',
  `component` varchar(256) DEFAULT NULL COMMENT '组件路径',
  `query` varchar(256) DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(64) NOT NULL DEFAULT '' COMMENT '路由名称',
  `is_frame` tinyint NOT NULL DEFAULT '1' COMMENT '是否为外链: 0-是, 1-否',
  `is_cache` tinyint NOT NULL DEFAULT '0' COMMENT '是否缓存: 0-缓存, 1-不缓存',
  `menu_type` char(1) NOT NULL DEFAULT '' COMMENT '菜单类型: M-目录, C-菜单, F-按钮',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否可见: 0-隐藏, 1-显示',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `perms` varchar(128) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(128) NOT NULL DEFAULT '#' COMMENT '菜单图标',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限表';

CREATE TABLE IF NOT EXISTS `admin_notice` (
  `id` bigint unsigned NOT NULL COMMENT '公告ID(雪花算法)',
  `notice_title` varchar(128) NOT NULL COMMENT '公告标题',
  `notice_type` tinyint NOT NULL COMMENT '公告类型: 1-通知, 2-公告',
  `notice_content` text COMMENT '公告内容',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-关闭, 1-正常',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  KEY `idx_notice_type` (`notice_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告表';

CREATE TABLE IF NOT EXISTS `admin_notice_read` (
  `id` bigint unsigned NOT NULL COMMENT 'ID(雪花算法)',
  `notice_id` bigint unsigned NOT NULL COMMENT '公告ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_notice` (`user_id`,`notice_id`),
  KEY `idx_notice_id` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告已读记录表';

CREATE TABLE IF NOT EXISTS `admin_oper_log` (
  `id` bigint unsigned NOT NULL COMMENT '日志ID(雪花算法)',
  `title` varchar(64) NOT NULL DEFAULT '' COMMENT '模块标题',
  `business_type` tinyint NOT NULL DEFAULT '0' COMMENT '业务类型: 0-其它, 1-新增, 2-修改, 3-删除, 4-授权, 5-导出, 6-导入, 7-强退, 8-生成, 9-清空',
  `method` varchar(256) NOT NULL DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) NOT NULL DEFAULT '' COMMENT '请求方式',
  `operator_type` tinyint NOT NULL DEFAULT '0' COMMENT '操作类别: 0-其它, 1-后台用户, 2-手机端用户',
  `oper_user_id` bigint unsigned DEFAULT NULL COMMENT '操作者用户ID',
  `oper_name` varchar(64) NOT NULL DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(64) DEFAULT NULL COMMENT '部门名称',
  `oper_url` varchar(256) NOT NULL DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) NOT NULL DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(256) NOT NULL DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) DEFAULT NULL COMMENT '请求参数',
  `json_result` varchar(2000) DEFAULT NULL COMMENT '返回参数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '操作状态: 0-成功, 1-失败',
  `error_msg` varchar(2000) DEFAULT NULL COMMENT '错误消息',
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time` bigint NOT NULL DEFAULT '0' COMMENT '消耗时间(毫秒)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_business_type` (`business_type`),
  KEY `idx_status` (`status`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_oper_user_id` (`oper_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志记录表';

CREATE TABLE IF NOT EXISTS `admin_post` (
  `id` bigint unsigned NOT NULL COMMENT '岗位ID(雪花算法)',
  `post_code` varchar(64) NOT NULL COMMENT '岗位编码',
  `post_name` varchar(64) NOT NULL COMMENT '岗位名称',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_code` (`post_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位表';

CREATE TABLE IF NOT EXISTS `admin_role` (
  `id` bigint unsigned NOT NULL COMMENT '角色ID(雪花算法)',
  `role_name` varchar(64) NOT NULL COMMENT '角色名称',
  `role_key` varchar(64) NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `data_scope` tinyint NOT NULL DEFAULT '1' COMMENT '数据范围: 1-全部, 2-自定义, 3-本部门, 4-本部门及以下',
  `menu_check_strictly` tinyint NOT NULL DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint NOT NULL DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `admin_role_dept` (
  `id` bigint unsigned NOT NULL COMMENT 'ID(雪花算法)',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `dept_id` bigint unsigned NOT NULL COMMENT '部门ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_dept` (`role_id`,`dept_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色和部门关联表';

CREATE TABLE IF NOT EXISTS `admin_role_menu` (
  `id` bigint unsigned NOT NULL COMMENT 'ID(雪花算法)',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `menu_id` bigint unsigned NOT NULL COMMENT '菜单ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色和菜单关联表';

CREATE TABLE IF NOT EXISTS `admin_user` (
  `id` bigint unsigned NOT NULL COMMENT '用户ID(雪花算法)',
  `dept_id` bigint unsigned DEFAULT NULL COMMENT '部门ID',
  `username` varchar(64) NOT NULL COMMENT '用户账号',
  `nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '用户昵称',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `sex` tinyint NOT NULL DEFAULT '2' COMMENT '性别: 0-男, 1-女, 2-未知',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `password` varchar(128) NOT NULL COMMENT '密码(BCrypt)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-停用, 1-正常',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `login_ip` varchar(128) DEFAULT NULL COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime DEFAULT NULL COMMENT '密码最后更新时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员用户表';

CREATE TABLE IF NOT EXISTS `admin_user_post` (
  `id` bigint unsigned NOT NULL COMMENT 'ID(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `post_id` bigint unsigned NOT NULL COMMENT '岗位ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post` (`user_id`,`post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与岗位关联表';

CREATE TABLE IF NOT EXISTS `admin_user_role` (
  `id` bigint unsigned NOT NULL COMMENT 'ID(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户和角色关联表';

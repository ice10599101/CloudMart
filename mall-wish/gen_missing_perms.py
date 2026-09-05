import io, os, re

# 1. 提取后端全部 @RequiresPermission 注解
backend_perms = set()
for search_dir in ['src/main/java', '../mall-admin/src/main/java']:
    for root, dirs, names in os.walk(search_dir):
        for n in names:
            if not n.endswith('.java'):
                continue
            fp = os.path.join(root, n)
            with io.open(fp, encoding='utf-8') as f:
                for line in f:
                    if '@RequiresPermission("' in line:
                        for m in re.finditer(r'@RequiresPermission\("([^"]+)"\)', line):
                            backend_perms.add(m.group(1))

# 2. DB 已有权限
db_perms = set()
with io.open('db_perms_snapshot.txt', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if line:
            db_perms.add(line)

missing = sorted(backend_perms - db_perms)
print(f'Backend: {len(backend_perms)}, DB: {len(db_perms)}, Missing: {len(missing)}')

# 3. 生成 V28 迁移 SQL
sql_lines = []
sql_lines.append('-- V28: 补齐后端注解有但 admin_menu 缺失的权限点（自动对照生成）')
sql_lines.append('-- 覆盖 @RequiresPermission 注解中所有在 admin_menu.perms 无对应行的权限')
sql_lines.append('')
sql_lines.append('INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES')

# 父菜单映射（按 perm 的 domain:sub 定位到对应 C 菜单行）
parent_map = {
    'admin:user': 1001, 'admin:role': 1010, 'admin:menu': 1020, 'admin:dept': 1006,
    'admin:post': 1025, 'admin:dict': 1030, 'admin:config': 1040, 'admin:notice': 1045,
    'admin:operlog': 1051, 'admin:loginlog': 1054, 'admin:online': 5004,
    'business:product': 2001, 'business:category': 2010, 'business:order': 2020,
    'business:coupon': 2030, 'business:member': 2040, 'business:seckill': 2050,
    'business:inventory': 2060, 'business:cart': 2090, 'business:review': 2035,
    'business:marketing': 2045, 'business:live': 2093, 'business:wms': 2094,
    'business:risk': 2098, 'business:shipping': 2130, 'business:warehouse': 2131,
    'business:file': 2132, 'business:brand': 2097,
    'business:wish': 3001, 'business:wishCategory': 3002, 'business:wishInteraction': 3003,
    'business:wishComment': 3004, 'business:wishBadge': 3005, 'business:wishBgm': 3006,
    'business:activity': 3007, 'business:capsule': 3015, 'business:aiPrompt': 3013,
    'business:aiConfig': 3013, 'business:aiReview': 2096, 'business:treeEnv': 3014,
    'business:matchGroup': 3008, 'business:matchConfig': 3008, 'business:leaderboard': 3009,
    'business:legacy': 3009, 'business:grayscale': 3010, 'business:map': 3012,
    'business:fence': 3012, 'business:warmEvent': 3012, 'business:encounter': 3012,
    'business:liveWidget': 3011, 'business:asset': 3018, 'business:wishBrand': 3018,
    'community:post': 4001, 'community:comment': 4004, 'community:tag': 4005,
    'community:badge': 4006, 'community:growth': 4007, 'community:review': 4002,
    'monitor:job': 5001, 'monitor:server': 5002, 'monitor:cache': 5003,
    'admin:online': 5004,
}

action_labels = {
    'list': '列表', 'query': '查询', 'add': '新增', 'edit': '编辑', 'remove': '删除',
    'delete': '删除', 'audit': '审核', 'score': '评分', 'trigger': '触发',
    'stats': '统计', 'sync': '同步', 'export': '导出', 'import': '导入',
    'reward': '发奖', 'forceLogout': '强制退出', 'resetPwd': '重置密码',
    'changeStatus': '状态变更', 'execute': '执行',
}

next_id = 4100
sql_values = []
for perm in missing:
    # 找父菜单
    parts = perm.split(':')
    parent_id = 2000
    if len(parts) >= 2:
        prefix = f'{parts[0]}:{parts[1]}'
        parent_id = parent_map.get(prefix, 2000)

    action = parts[-1] if len(parts) > 2 else perm
    label = action_labels.get(action, action)
    menu_name = f'{label} ({perm})'.replace("'", "''")

    while next_id in used_ids:
        next_id += 1
    used_ids.add(next_id)

    sql_values.append(f"({next_id}, '{menu_name}', {parent_id}, 99, '', 'F', 1, 1, '{perm}', '#', NOW(), NOW())")

sql_lines.append(',\n'.join(sql_values))
sql_lines.append(';')
sql_lines.append('')
sql_lines.append('-- 绑超管角色')
sql_lines.append('INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)')
sql_lines.append('SELECT 1, id, NOW(), NOW() FROM admin_menu WHERE deleted_at IS NULL AND id >= 4100;')

with io.open('src/main/resources/db/migration/V28__missing_permissions.sql', 'w', encoding='utf-8', newline='') as f:
    f.write('\n'.join(sql_lines))
print(f'V28 written with {len(missing)} permissions')

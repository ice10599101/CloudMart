// 路由权限清单守门脚本（E01/S05）：
//   1. 顶层键重复检测 —— YAML 重复键会被部分解析器静默覆盖，导致策略漂移
//   2. flow mapping 内含 { } 的 path 值必须加引号 —— 否则不是合法 YAML
//   3. 每条路由必须具备 method/path/subjectType，subjectType 取值必须合法
// 独立于具体 YAML 解析器，零第三方依赖，供 CI 与本地 node 直接执行。
import { readFileSync } from 'node:fs';

const FILE = 'docs/route-policy.yaml';
const VALID_SUBJECTS = new Set(['USER', 'ADMIN', 'SERVICE', 'ANON']);

const errors = [];
const lines = readFileSync(FILE, 'utf8').split(/\r?\n/);

const topLevelKeys = new Map();
lines.forEach((line, idx) => {
  const lineNo = idx + 1;
  if (line.trim().startsWith('#')) {
    return;
  }
  const keyMatch = line.match(/^([A-Za-z][A-Za-z0-9_-]*):/);
  if (keyMatch) {
    const key = keyMatch[1];
    if (topLevelKeys.has(key)) {
      errors.push(`L${lineNo}: 顶层键重复 "${key}"（首次出现于 L${topLevelKeys.get(key)}）`);
    } else {
      topLevelKeys.set(key, lineNo);
    }
  }

  if (!line.trim().startsWith('- {')) {
    return;
  }

  const pathMatch = line.match(/\bpath:\s*("[^"]*"|[^,]+?)\s*,\s*(?:subjectType|note|ownerCheck|permission|serviceCaller)\b/);
  const pathValue = pathMatch?.[1]?.trim() ?? '';
  if (!pathValue) {
    errors.push(`L${lineNo}: 无法解析 path 字段`);
  } else if ((pathValue.includes('{') || pathValue.includes('}')) && !/^".*"$/.test(pathValue)) {
    errors.push(`L${lineNo}: path 含 { } 但未加引号，不是合法 YAML：${pathValue}`);
  }

  const subjectMatch = line.match(/\bsubjectType:\s*"?([A-Z_,\s]+)"?/);
  if (!subjectMatch) {
    errors.push(`L${lineNo}: 缺少 subjectType`);
  } else {
    for (const subject of subjectMatch[1].split(',').map((s) => s.trim()).filter(Boolean)) {
      if (!VALID_SUBJECTS.has(subject)) {
        errors.push(`L${lineNo}: 非法 subjectType "${subject}"`);
      }
    }
  }

  if (!/\bmethod:\s*"?[A-Za-z*]+"?/.test(line)) {
    errors.push(`L${lineNo}: 缺少 method`);
  }
});

if (errors.length > 0) {
  console.error(`route-policy lint FAIL (${errors.length} 处):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
console.log(`route-policy lint PASS: ${topLevelKeys.size} 个顶层键，语法与必填字段完整`);

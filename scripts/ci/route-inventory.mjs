// 路由清单对照门禁（T25 落地 / R27 后降级为辅助检查）：
//   ⚠️ R27 起，权威路由门禁为各服务 src/test/**/routeguard/RouteGuardTest
//   （基于 Spring RequestMappingHandlerMapping 真实解析，覆盖全限定注解、
//   value/path、多路径、类级继承、mall-common @RequiresPermission/@RequiresAdmin，
//   含 mall-pet；存量豁免逐项目录在各服务 src/test/resources/route-guard.json）。
//
//   本脚本保留为 route-policy.yaml 声明完整性的辅助 lint（正则扫描，已知盲区：
//   不扫描 mall-pet；守卫在 mapping 注解上一行时不识别），其"已归类"口径不代表
//   全部写路由均有真实守卫——权威结论以 Java 门禁 [R27] 输出（覆盖/公开/豁免/违规）
//   为准。R27 已修正：空 value 裸注解按根路径处理（不再跨行误读兄弟注解字符串），
//   守卫识别支持全限定 @PreAuthorize 与 @RequiresAdmin。
//
//   1. 扫描全部 mall-*/src/main/java/**/controller/*.java 的类级 @RequestMapping 前缀
//      与方法级 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping，
//      枚举真实 HTTP 路由清单（method + path + 是否带 @PreAuthorize）。
//   2. 对照 docs/route-policy.yaml：写路由（POST/PUT/DELETE/PATCH）必须满足其一——
//        a) 命中策略条目（method 匹配，路径段级匹配：{var} 通配任意段、** 前缀通配）；
//        b) 方法或类带 @PreAuthorize（显式权限注解）。
//      未归类的新增写路由 → 退出码 1 阻断 CI。
//   3. /internal/** 与 /admin/** 写端点额外要求 @PreAuthorize（纵深防御：Spring
//      Security 通配规则之外，端点自带语义守卫）。
// 零第三方依赖，线性扫描（无嵌套量词回溯），供 CI 与本地 node 直接执行。
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const POLICY_FILE = join(ROOT, 'docs', 'route-policy.yaml');

const ALLOWLIST_FILE = join(ROOT, 'scripts', 'ci', 'route-inventory-allowlist.json');
const BASELINE_FILE = join(ROOT, 'scripts', 'ci', 'route-inventory-baseline.json');

const errors = [];
const inventory = [];

function loadAllowlist() {
  try {
    return JSON.parse(readFileSync(ALLOWLIST_FILE, 'utf8')).entries ?? [];
  } catch {
    return [];
  }
}

/** 存量欠账基线：治理目标是清零；CI 只对基线外的新增未归类阻断 */
function loadBaseline() {
  try {
    return JSON.parse(readFileSync(BASELINE_FILE, 'utf8')).entries ?? [];
  } catch {
    return [];
  }
}

// ---------- 1. 策略条目解析（复用 lint 的行式 { } 语法） ----------
function parsePolicy() {
  const entries = [];
  let currentService = null;
  const raw = readFileSync(POLICY_FILE, 'utf8').split(/\r?\n/);
  // 多行 flow mapping 重组：'- {' 起到 '}' 止合并为单行再解析
  const lines = [];
  let buffer = null;
  for (const line of raw) {
    if (buffer === null && line.trim().startsWith('- {')) {
      buffer = line;
      if (line.includes('}')) { lines.push(buffer); buffer = null; }
      continue;
    }
    if (buffer !== null) {
      buffer += ' ' + line.trim();
      if (line.includes('}')) { lines.push(buffer); buffer = null; }
      continue;
    }
    lines.push(line);
  }
  for (const line of lines) {
    // 顶层服务键（mall-auth: / mall-job: 等）——策略按服务作用域匹配
    const serviceKey = line.match(/^([a-z][a-z0-9-]*):\s*$/);
    if (serviceKey) {
      currentService = serviceKey[1];
      continue;
    }
    if (line.trim().startsWith('#')) continue;
    if (!line.trim().startsWith('- {')) continue;
    const methodMatch = line.match(/\bmethod:\s*"?([A-Za-z*]+)"?/);
    const pathMatch = line.match(/\bpath:\s*("[^"]*"|[^,]+?),/);
    const subjectMatch = line.match(/\bsubjectType:\s*"?([A-Z_,\s]+)"?/);
    if (!methodMatch || !pathMatch) continue;
    entries.push({
      service: currentService,
      method: methodMatch[1].toUpperCase(),
      path: pathMatch[1].trim().replace(/^"|"$/g, ''),
      subjects: subjectMatch ? subjectMatch[1].split(',').map((s) => s.trim()).filter(Boolean) : [],
    });
  }
  return entries;
}

// ---------- 2. Controller 扫描（行式，线性时间） ----------
function listJavaFiles(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...listJavaFiles(full));
    else if (name.endsWith('Controller.java')) out.push(full);
  }
  return out;
}

function extractAnnPath(line) {
  const m = line.match(/"([^"]*)"/);
  return m ? m[1] : '';
}

/**
 * 提取方法级 mapping 注解路径：仅在注解括号未闭合（多行注解）时向下跨行收集，
 * 直到括号平衡。裸注解（如 @DeleteMapping）或同行已闭合时，绝不读取下一行——
 * 下一行可能是兄弟注解（如 @PreAuthorize("isAuthenticated()")），其字符串
 * 不是路径（R27：旧回退逻辑曾把守卫表达式误解析为 /isAuthenticated()）。
 */
function extractMappingPath(lines, i) {
  const annIdx = lines[i].search(/@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b/);
  const head = lines[i].slice(annIdx);
  let depth = 0;
  let text = '';
  for (let k = i; k < lines.length; k++) {
    const chunk = k === i ? head : lines[k];
    text += ' ' + chunk;
    for (const ch of chunk) {
      if (ch === '(') depth++;
      else if (ch === ')') depth--;
    }
    if (depth <= 0) break;
  }
  const m = text.match(/"([^"]*)"/);
  return m ? m[1] : '';
}

// 守卫注解：Spring 简名/全限定 @PreAuthorize，mall-common @RequiresPermission/@RequiresAdmin
const GUARD_ANNOTATION_RE =
  /^\s*@(org\.springframework\.security\.access\.prepost\.)?PreAuthorize\b|^\s*@(com\.cloudmart\.common\.annotation\.)?Requires(Permission|Admin)\b/;
const mapRe = /^\s*@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b/;

function scanController(mod, file) {
  const src = readFileSync(file, 'utf8');
  const lines = src.split(/\r?\n/);

  // 类级前缀：首个 @RequestMapping("...")
  let classPrefix = '';
  let classPreAuth = false;
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*public class /.test(lines[i])) break;
    if (!classPrefix && /^\s*@RequestMapping\(\s*"/.test(lines[i])) {
      classPrefix = extractAnnPath(lines[i]).replace(/\/$/, '');
    }
    // 守卫识别口径与 Java RouteGuardTest 对齐：支持全限定写法与 mall-common 两个注解
    if (GUARD_ANNOTATION_RE.test(lines[i])) classPreAuth = true;
  }

  for (let i = 0; i < lines.length; i++) {
    const am = lines[i].match(mapRe);
    if (!am) continue;
    const httpMethod = am[1].replace(/Mapping$/, '').toUpperCase();
    const subPath = extractMappingPath(lines, i);

    // 注解区：向下到下一个 mapping 注解/类结束为止；途中遇 public 即处理器方法
    // （@Operation description 等可跨多行，固定行数窗口会漏扫）
    let preAuth = classPreAuth;
    let isHandler = false;
    for (let j = i + 1; j < lines.length; j++) {
      if (GUARD_ANNOTATION_RE.test(lines[j])) preAuth = true;
      if (/^\s*public\s/.test(lines[j])) { isHandler = true; break; }
      if (mapRe.test(lines[j])) break; // 下一个 mapping：当前条目非处理器
      if (/^\s*public class /.test(lines[j])) break;
      if (/^\s*@/.test(lines[j])) continue;
      break; // 非注解非 public：字段/其他声明，停止
    }
    if (!isHandler) continue;

    const full = normalizePath(classPrefix + '/' + subPath);
    inventory.push({
      module: mod,
      method: httpMethod,
      path: full,
      preAuth,
      file: file.replace(/\\/g, '/').replace(ROOT.replace(/\\/g, '/') + '/', ''),
    });
  }
}

function normalizePath(p) {
  let out = '/' + p.split('/').filter((s) => s && s !== '').join('/');
  out = out.replace(/\/\*\*(\/\*\*)+/g, '/**');
  return out === '' ? '/' : out;
}

function scanAll() {
  // 宠物域不在本轮改造范围（方案 §2.3 审计边界 / T26：不改宠物业务）
  const modules = readdirSync(ROOT).filter((n) => /^mall-[a-z]+$/.test(n) && n !== 'mall-pet');
  for (const mod of modules) {
    const ctrlRoot = join(ROOT, mod, 'src', 'main', 'java');
    let files = [];
    try {
      files = listJavaFiles(ctrlRoot);
    } catch {
      continue;
    }
    for (const file of files) scanController(mod, file);
  }
}

// ---------- 3. 匹配判定 ----------
function pathMatches(route, policy) {
  const rSegs = route.split('/').filter(Boolean);
  const pSegs = policy.split('/').filter(Boolean);
  for (let i = 0; i < pSegs.length; i++) {
    if (pSegs[i] === '**') return true;
    if (i >= rSegs.length) return false;
    if (pSegs[i].startsWith('{')) continue; // 路径变量通配任意单段
    if (pSegs[i] !== rSegs[i]) return false;
  }
  return rSegs.length === pSegs.length;
}

function isCoveredByPolicy(service, method, route, policy) {
  return policy.some(
    (e) =>
      e.service === service &&
      (e.method === '*' || e.method === method) &&
      pathMatches(route, e.path),
  );
}

// ---------- 4. 主流程 ----------
const policy = parsePolicy();
scanAll();

const allowlist = loadAllowlist();
const isAllowlisted = (r) =>
  allowlist.some((a) => a.method === r.method && a.path === r.path && a.module === r.module);

const baseline = loadBaseline();
const baselineKey = (r) => `${r.module}|${r.method}|${r.path}`;
const baselineSet = new Set(baseline.map((e) => `${e.module}|${e.method}|${e.path}`));

const WRITE = new Set(['POST', 'PUT', 'DELETE', 'PATCH']);
const writeRoutes = inventory.filter((r) => WRITE.has(r.method));
const unclassifiedAll = writeRoutes.filter(
  (r) => !r.preAuth && !isCoveredByPolicy(r.module, r.method, r.path, policy),
);
// 存量在基线中的仅提示；基线外（新增未归类）阻断
const unclassified = unclassifiedAll.filter((r) => !baselineSet.has(baselineKey(r)));
const legacyCount = unclassifiedAll.length - unclassified.length;


const bareInternal = inventory.filter(
  (r) =>
    WRITE.has(r.method) &&
    !r.preAuth &&
    !isAllowlisted(r) &&
    (r.path.startsWith('/internal/') || r.path.startsWith('/admin/')),
);

console.log(
  `路由清单: 共 ${inventory.length} 条（写路由 ${writeRoutes.length} 条；策略条目 ${policy.length} 条；` +
    `存量欠账 ${legacyCount} 条在基线内，新增未归类 ${unclassified.length} 条）`,
);

if (unclassified.length > 0) {
  errors.push(`未归类的写路由 ${unclassified.length} 条（既无 @PreAuthorize 也未在 route-policy.yaml 声明）：`);
  for (const r of unclassified) errors.push(`  [${r.module}] ${r.method} ${r.path}  (${r.file})`);
}
if (bareInternal.length > 0) {
  errors.push(`admin/internal 写端点缺少 @PreAuthorize 守卫 ${bareInternal.length} 条：`);
  for (const r of bareInternal) errors.push(`  [${r.module}] ${r.method} ${r.path}  (${r.file})`);
}

if (errors.length > 0) {
  console.error('route-inventory FAIL（T25）：');
  for (const e of errors) console.error(e);
  console.error(
    '\n处置：为写路由补 @PreAuthorize，或在 docs/route-policy.yaml 增补策略条目（含 subjectType/serviceCaller/ownerCheck）。',
  );
  process.exit(1);
}
console.log(
  `route-inventory PASS（T25 辅助检查；R27 权威注解门禁见各服务 RouteGuardTest）: 共 ${inventory.length} 条`
  + `（写路由 ${writeRoutes.length} 条；策略条目 ${policy.length} 条；`
  + `存量欠账 ${legacyCount} 条在基线内，新增未归类 ${unclassified.length} 条）；`
  + '注意：本结果不含 mall-pet，真实覆盖数以 Java 门禁为准。');

// 生成存量未归类写路由基线（一次性工具；治理目标清零：修复一条删一条）。
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const POLICY_FILE = join(ROOT, 'docs', 'route-policy.yaml');
const inventory = [];

function parsePolicy() {
  const entries = [];
  let currentService = null;
  const raw = readFileSync(POLICY_FILE, 'utf8').split(/\r?\n/);
  for (const line of raw) {
    const serviceKey = line.match(/^([a-z][a-z0-9-]*):\s*$/);
    if (serviceKey) { currentService = serviceKey[1]; continue; }
    if (line.trim().startsWith('#')) continue;
    if (!line.trim().startsWith('- {')) continue;
    const methodMatch = line.match(/\bmethod:\s*"?([A-Za-z*]+)"?/);
    const pathMatch = line.match(/\bpath:\s*("[^"]*"|[^,]+?),/);
    if (!methodMatch || !pathMatch) continue;
    entries.push({ service: currentService, method: methodMatch[1].toUpperCase(),
      path: pathMatch[1].trim().replace(/^"|"$/g, '') });
  }
  return entries;
}

function listJavaFiles(dir) {
  const out = [];
  for (const n of readdirSync(dir)) {
    const f = join(dir, n);
    if (statSync(f).isDirectory()) out.push(...listJavaFiles(f));
    else if (n.endsWith('Controller.java')) out.push(f);
  }
  return out;
}

function normalizePath(p) {
  let out = '/' + p.split('/').filter((x) => x && x !== '').join('/');
  out = out.replace(/\/\*\*(\/\*\*)+/g, '/**');
  return out === '' ? '/' : out;
}

function pathMatches(route, policy) {
  const r = route.split('/').filter(Boolean);
  const q = policy.split('/').filter(Boolean);
  for (let i = 0; i < q.length; i++) {
    if (q[i] === '**') return true;
    if (i >= r.length) return false;
    if (q[i].startsWith('{')) continue;
    if (q[i] !== r[i]) return false;
  }
  return r.length === q.length;
}

function covered(service, method, route, policy) {
  return policy.some((e) => e.service === service
    && (e.method === '*' || e.method === method) && pathMatches(route, e.path));
}

const policy = parsePolicy();
const WRITE = new Set(['POST', 'PUT', 'DELETE', 'PATCH']);
const mapRe = /^\s*@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b/;
const entries = [];
const modules = readdirSync(ROOT).filter((n) => /^mall-[a-z]+$/.test(n) && n !== 'mall-pet');
for (const mod of modules) {
  let files = [];
  try { files = listJavaFiles(join(ROOT, mod, 'src', 'main', 'java')); } catch { continue; }
  for (const file of files) {
    const lines = readFileSync(file, 'utf8').split(/\r?\n/);
    let classPrefix = '';
    let classPreAuth = false;
    for (let i = 0; i < lines.length; i++) {
      if (/^\s*public class /.test(lines[i])) break;
      if (!classPrefix && /^\s*@RequestMapping\(\s*"/.test(lines[i])) {
        const m = lines[i].match(/"([^"]*)"/);
        classPrefix = m ? m[1].replace(/\/$/, '') : '';
      }
      if (/^\s*@(PreAuthorize|RequiresPermission)/.test(lines[i])) classPreAuth = true;
    }
    for (let i = 0; i < lines.length; i++) {
      const am = lines[i].match(mapRe);
      if (!am) continue;
      const method = am[1].replace(/Mapping$/, '').toUpperCase();
      let l1 = lines[i].match(/"([^"]*)"/);
      let subPath = l1 ? l1[1] : '';
      if (!subPath && lines[i + 1]) {
        const m2 = lines[i + 1].match(/"([^"]*)"/);
        subPath = m2 ? m2[1] : '';
      }
      let preAuth = classPreAuth;
      let isHandler = false;
      for (let j = i + 1; j < lines.length; j++) {
        if (/^\s*@(PreAuthorize|RequiresPermission)/.test(lines[j])) preAuth = true;
        if (/^\s*public\s/.test(lines[j])) { isHandler = true; break; }
        if (mapRe.test(lines[j])) break;
        if (/^\s*public class /.test(lines[j])) break;
        if (/^\s*@/.test(lines[j])) continue;
        break;
      }
      if (!isHandler || !WRITE.has(method)) continue;
      const full = normalizePath(classPrefix + '/' + subPath);
      if (!preAuth && !covered(mod, method, full, policy)) {
        entries.push({
          module: mod, method, path: full,
          file: file.replace(/\\/g, '/').replace(ROOT.replace(/\\/g, '/') + '/', ''),
        });
      }
    }
  }
}

const out = {
  comment: 'T25 存量未归类写路由基线（生成于本轮审计）——治理目标清零：修复一条删一条；'
    + '新增未归类路由将被 CI 阻断，禁止把新条目加入本文件。',
  generatedIn: 'CloudMart 全栈审计改造（T25）',
  entries,
};
writeFileSync(join(ROOT, 'scripts', 'ci', 'route-inventory-baseline.json'),
  JSON.stringify(out, null, 2) + '\n');
console.log('baseline entries:', entries.length);

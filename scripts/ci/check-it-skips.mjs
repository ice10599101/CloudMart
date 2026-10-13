// R28 集成测试跳过精确门禁：
//   扫描 failsafe XML 报告中所有含 <skipped> 的用例，与 scripts/ci/it-skip-allowlist.json
//   逐条精确比对（类全限定名 或 类#方法），替代旧 CI 中"只数 skipped 数量==3"的口径——
//   数量白名单会让新增禁用与意外恢复互相抵消。
//
//   规则：
//   1. 每个 skipped 用例必须命中一条未到期的白名单条目（match=method 精确到方法；
//      match=class 要求该类全部用例均 skipped，类级 @Disabled 的标准形态）；
//   2. 每条白名单必须实际命中至少一个 skipped 用例（禁用解除/测试恢复后必须删条目）；
//   3. expires < today（UTC 日期）即失败，禁止无期限跳过；
//   4. 白名单为空且没有 skipped 用例 → PASS（目标态）。
//
// 用法：node scripts/ci/check-skips.mjs [reportsDir ...]   默认扫描 mall-*/target/failsafe-reports
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
export const ALLOWLIST_FILE = join(ROOT, 'scripts', 'ci', 'it-skip-allowlist.json');

/** 从单个 failsafe TEST-*.xml 提取全部用例与其中 skipped 用例 */
export function parseReport(xmlText) {
  const all = [];
  const skipped = [];
  const caseRe = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  let m;
  while ((m = caseRe.exec(xmlText)) !== null) {
    const attrs = m[1] ?? '';
    const body = m[2] ?? '';
    const name = (attrs.match(/\bname="([^"]*)"/) || [])[1];
    const classname = (attrs.match(/\bclassname="([^"]*)"/) || [])[1];
    if (!name || !classname) continue;
    const c = { id: `${classname}#${name}`, className: classname, method: name };
    all.push(c);
    // 自闭合 testcase 不可能含 skipped；body 中出现 <skipped> 即被跳过
    if (/<skipped\b/.test(body)) skipped.push(c);
  }
  return { all, skipped };
}

/** 读取目录下全部 TEST-*.xml，汇总全部用例与 skipped 用例 */
export function collectReports(reportsDirs) {
  const all = [];
  const skipped = [];
  for (const dir of reportsDirs) {
    if (!existsSync(dir)) continue;
    for (const name of readdirSync(dir)) {
      if (!/^TEST-.*\.xml$/.test(name)) continue;
      const r = parseReport(readFileSync(join(dir, name), 'utf8'));
      all.push(...r.all);
      skipped.push(...r.skipped);
    }
  }
  return { all, skipped };
}

function loadAllowlist(file = ALLOWLIST_FILE) {
  const doc = JSON.parse(readFileSync(file, 'utf8'));
  return Array.isArray(doc.entries) ? doc.entries : [];
}

/**
 * 评估 skipped 用例与白名单的一致性。
 * @param {{id:string,className:string,method:string}[]} allCases 报告中全部用例（识别类内部分恢复）
 * @param {{id:string,className:string,method:string}[]} skippedCases 其中被跳过的用例
 * @param {Array<{id:string,match:string,reason:string,owner:string,expires:string}>} entries
 * @param {string} today UTC YYYY-MM-DD
 * @returns {{errors:string[], matched:Map<number,number>}}
 */
export function evaluateSkips(allCases, skippedCases, entries, today) {
  const errors = [];
  const matched = new Map();

  for (const [idx, e] of entries.entries()) {
    if (!e.id || !e.match || !e.reason || !e.owner || !e.expires) {
      errors.push(`白名单第 ${idx + 1} 条字段不完整（id/match/reason/owner/expires 必填）: ${JSON.stringify(e)}`);
      continue;
    }
    if (!['class', 'method'].includes(e.match)) {
      errors.push(`白名单 ${e.id} 的 match 只能是 class|method，实际=${e.match}`);
      continue;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(e.expires)) {
      errors.push(`白名单 ${e.id} 的 expires 必须为 YYYY-MM-DD`);
      continue;
    }
    if (e.expires < today) {
      errors.push(`白名单 ${e.id} 已于 ${e.expires} 到期（今日 ${today}）——恢复测试或重新评审，禁止无期限跳过`);
    }
  }

  const validEntries = entries.filter(
    (e) => e.id && ['class', 'method'].includes(e.match) && /^\d{4}-\d{2}-\d{2}$/.test(e.expires),
  );

  for (const c of skippedCases) {
    const hits = validEntries.filter((e) =>
      e.match === 'method' ? c.id === e.id : c.className === e.id,
    );
    if (hits.length === 0) {
      errors.push(`未核准的跳过用例（不在 it-skip-allowlist.json）：${c.id}`);
    } else if (hits.length > 1) {
      errors.push(`跳过用例 ${c.id} 命中多条白名单（${hits.map((h) => h.id).join(', ')}），白名单必须互斥`);
    } else {
      const idx = entries.indexOf(hits[0]);
      matched.set(idx, (matched.get(idx) ?? 0) + 1);
    }
  }

  // class 级条目的附加约束：该类出现在报告中的全部用例必须都被 skipped（标准类级 @Disabled），
  // 防止类里一半恢复一半禁用却被类级白条掩盖
  for (const [, e] of entries.entries()) {
    if (!e.id || e.match !== 'class') continue;
    const skippedOfClass = new Set(skippedCases.filter((c) => c.className === e.id).map((c) => c.id));
    const allOfClass = new Set(allCases.filter((c) => c.className === e.id).map((c) => c.id));
    if (skippedOfClass.size === 0) {
      errors.push(`白名单 ${e.id}（class）未命中任何 skipped 用例——测试已恢复？请立即删除该条目`);
    }
    const ran = [...allOfClass].filter((id) => !skippedOfClass.has(id));
    if (ran.length > 0) {
      errors.push(
        `白名单 ${e.id}（class）只覆盖类级禁用，但该类有用例实际执行：${ran.join(', ')}`
        + '——请将白名单改为 method 级精确条目',
      );
    }
  }
  for (const [idx, e] of entries.entries()) {
    if (e.match === 'method' && !matched.has(idx)) {
      errors.push(`白名单 ${e.id}（method）未命中 skipped 用例——测试已恢复？请立即删除该条目`);
    }
  }

  return { errors, matched };
}

function main() {
  const today = new Date().toISOString().slice(0, 10);
  const args = process.argv.slice(2);
  const dirs = args.length > 0
    ? args
    : readdirSync(ROOT).filter((n) => /^mall-/.test(n)).map((n) => join(ROOT, n, 'target', 'failsafe-reports'));
  const { all, skipped } = collectReports(dirs);
  const entries = loadAllowlist();
  const { errors } = evaluateSkips(all, skipped, entries, today);

  const unique = [...new Set(skipped.map((c) => c.id))].sort();
  console.log(`[R28] failsafe skipped 用例 ${unique.length} 个（去重），白名单 ${entries.length} 条`);
  for (const id of unique) console.log(`  skipped: ${id}`);

  if (errors.length > 0) {
    console.error('check-it-skips FAIL（R28 精确跳过门禁）：');
    for (const e of errors) console.error(`  - ${e}`);
    process.exit(1);
  }
  console.log('check-it-skips PASS：全部 skipped 用例均命中未到期的精确白名单，且无失效条目');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}

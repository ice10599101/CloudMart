import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseReport, evaluateSkips } from './check-it-skips.mjs';

const VECTOR_XML = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.cloudmart.ai.service.VectorSearchIntegrationTest" tests="3" skipped="3">
  <testcase name="addAndSearch_shouldReturnDocument" classname="com.cloudmart.ai.service.VectorSearchIntegrationTest" time="0">
    <skipped message="ES 9 knn 不兼容"/>
  </testcase>
  <testcase name="delete_shouldRemoveDocument" classname="com.cloudmart.ai.service.VectorSearchIntegrationTest" time="0">
    <skipped/>
  </testcase>
  <testcase name="similaritySearch_shouldRespectTopK" classname="com.cloudmart.ai.service.VectorSearchIntegrationTest" time="0.001">
<skipped></skipped>
  </testcase>
</testsuite>`;

const vectorEntry = {
  id: 'com.cloudmart.ai.service.VectorSearchIntegrationTest',
  match: 'class',
  reason: 'ES 9.0.2 与 Spring AI knn DSL 不兼容',
  owner: 'platform/ai',
  expires: '2027-06-30',
};

test('parseReport: 提取全部用例并识别 skipped（三种 skipped 标签形态）', () => {
  const { all, skipped } = parseReport(VECTOR_XML);
  assert.equal(all.length, 3);
  assert.deepEqual(skipped.map((c) => c.method).sort(), [
    'addAndSearch_shouldReturnDocument',
    'delete_shouldRemoveDocument',
    'similaritySearch_shouldRespectTopK',
  ]);
});

test('parseReport: 正常执行的用例与自闭合用例不算 skipped', () => {
  const xml = `<testsuite>
    <testcase name="green" classname="x.It"/>
    <testcase name="selfClosed" classname="x.It2"/>
  </testsuite>`;
  const { all, skipped } = parseReport(xml);
  assert.equal(all.length, 2);
  assert.equal(skipped.length, 0);
});

test('正常态：类级白名单精确覆盖全部 skipped → PASS', () => {
  const { all, skipped } = parseReport(VECTOR_XML);
  const { errors } = evaluateSkips(all, skipped, [vectorEntry], '2026-10-13');
  assert.deepEqual(errors, []);
});

test('门禁红：未在白名单的跳过用例（新增 @Disabled 必须评审）', () => {
  const xml = `<testsuite>
    <testcase name="rogue" classname="com.cloudmart.order.OrderIt"><skipped/></testcase>
  </testsuite>`;
  const { all, skipped } = parseReport(xml);
  const { errors } = evaluateSkips(all, skipped, [], '2026-10-13');
  assert.equal(errors.length, 1);
  assert.match(errors[0], /未核准的跳过用例.*com\.cloudmart\.order\.OrderIt#rogue/);
});

test('门禁红：白名单到期自动失效（即使 skipped 仍存在）', () => {
  const { all, skipped } = parseReport(VECTOR_XML);
  const expired = { ...vectorEntry, expires: '2026-10-12' };
  const { errors } = evaluateSkips(all, skipped, [expired], '2026-10-13');
  assert.ok(errors.some((e) => /已于 2026-10-12 到期/.test(e)), errors.join('\n'));
});

test('门禁红：测试已恢复但白名单未删除（防白条堆积）', () => {
  const xml = `<testsuite>
    <testcase name="green" classname="com.cloudmart.ai.service.VectorSearchIntegrationTest"/>
  </testsuite>`;
  const { all, skipped } = parseReport(xml);
  const { errors } = evaluateSkips(all, skipped, [vectorEntry], '2026-10-13');
  assert.ok(errors.some((e) => /未命中任何 skipped 用例/.test(e)), errors.join('\n'));
});

test('门禁红：类级白名单但类内部分用例已恢复执行（要求降级为 method 级）', () => {
  const xml = `<testsuite>
    <testcase name="stillSkipped" classname="com.cloudmart.ai.service.VectorSearchIntegrationTest"><skipped/></testcase>
    <testcase name="recovered" classname="com.cloudmart.ai.service.VectorSearchIntegrationTest"/>
  </testsuite>`;
  const { all, skipped } = parseReport(xml);
  const { errors } = evaluateSkips(all, skipped, [vectorEntry], '2026-10-13');
  assert.ok(errors.some((e) => /实际执行.*recovered/.test(e)), errors.join('\n'));
});

test('门禁红：同一 skipped 用例命中多条互斥白名单', () => {
  const xml = `<testsuite>
    <testcase name="m" classname="c.It"><skipped/></testcase>
  </testsuite>`;
  const entries = [
    { id: 'c.It', match: 'class', reason: 'r', owner: 'o', expires: '2027-01-01' },
    { id: 'c.It#m', match: 'method', reason: 'r', owner: 'o', expires: '2027-01-01' },
  ];
  const { all, skipped } = parseReport(xml);
  const { errors } = evaluateSkips(all, skipped, entries, '2026-10-13');
  assert.ok(errors.some((e) => /命中多条白名单/.test(e)), errors.join('\n'));
});

test('门禁红：白名单字段缺失或 expires 格式非法', () => {
  const { all, skipped } = parseReport(VECTOR_XML);
  const bad = [{ id: 'x', match: 'class', reason: '', owner: 'o', expires: '2027-06-30' }];
  const r1 = evaluateSkips(all, skipped, bad, '2026-10-13');
  assert.ok(r1.errors.some((e) => /字段不完整/.test(e)));
  const bad2 = [{ ...vectorEntry, expires: '2027/06/30' }];
  const r2 = evaluateSkips(all, skipped, bad2, '2026-10-13');
  assert.ok(r2.errors.some((e) => /YYYY-MM-DD/.test(e)));
});

test('目标态：空白名单 + 零跳过 → PASS', () => {
  const { errors } = evaluateSkips([], [], [], '2026-10-13');
  assert.deepEqual(errors, []);
});

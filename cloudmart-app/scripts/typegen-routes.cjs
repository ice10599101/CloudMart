/**
 * R28：在 CI 干净环境（无 .expo 缓存）中、tsc 之前生成 Expo Router 类型路由声明。
 *
 * 为什么需要：app.json 开启 experiments.typedRoutes 后，.expo/types/router.d.ts
 * 提供 href 字面量联合类型（错误路径在类型期即可发现）；该文件默认仅由
 * `expo start` / `expo export` 期间的 Metro watcher 生成，而 CI 门禁顺序是
 * 先 tsc 后 export，缺失时 Href 静默回退为宽 string，类型门禁形同虚设。
 *
 * 实现：直接复用 @expo/cli 内置的同一生成器（regenerateDeclarations），
 * 不自行解析 app 目录，避免与 Expo Router 路由语义漂移。SDK 升级后若内部
 * 路径变化，本脚本会明确报错并指向修复点。
 */
const path = require('node:path');
const fs = require('node:fs');

const projectRoot = path.resolve(__dirname, '..');
// Expo Router 约定：src/app 优先（本项目布局），其次根 app/
const appRootCandidates = [path.join(projectRoot, 'src', 'app'), path.join(projectRoot, 'app')];
const appRoot = appRootCandidates.find((p) => fs.existsSync(p));
const outputDir = path.join(projectRoot, '.expo', 'types');

if (!appRoot) {
  console.error(`[typegen-routes] 未找到 Expo Router 目录（已查找 ${appRootCandidates.join('、')}）`);
  process.exit(1);
}

// 注意：typed-routes 模块在加载瞬间即用 process.env.EXPO_ROUTER_APP_ROOT 创建路由
// 上下文（模块级 defaultCtx），因此必须先设置环境变量再 require，顺序不可调换。
process.env.EXPO_ROUTER_APP_ROOT = appRoot;

let typedRoutesModule;
try {
  // @expo/router-server 随 @expo/cli 嵌套安装（npm 不提升时位于
  // expo/node_modules/@expo/cli/node_modules/@expo/router-server）；
  // 依次尝试提升位置与嵌套位置，兼容不同包管理器布局
  const expoDir = path.dirname(require.resolve('expo/package.json', { paths: [projectRoot] }));
  const subpath = path.join('@expo', 'router-server', 'build', 'typed-routes');
  const candidates = [
    path.join(expoDir, 'node_modules', '@expo', 'cli', 'node_modules', subpath),
    path.join(expoDir, 'node_modules', subpath),
    path.join(projectRoot, 'node_modules', subpath),
  ];
  const entry = candidates.find((p) => fs.existsSync(path.join(p, 'index.js')));
  if (!entry) {
    throw new Error(`候选路径均不存在：\n${candidates.join('\n')}`);
  }
  typedRoutesModule = require(entry);
} catch (err) {
  console.error('[typegen-routes] 无法加载 @expo/router-server 类型路由生成器。');
  console.error('Expo SDK 升级后内部路径可能变化，请在更新 expo/expo-router 后修复本脚本。');
  console.error(err && err.message ? err.message : err);
  process.exit(1);
}

if (typeof typedRoutesModule.regenerateDeclarations !== 'function') {
  console.error('[typegen-routes] regenerateDeclarations 导出缺失，Expo SDK 内部 API 已变化，需更新本脚本。');
  process.exit(1);
}

fs.mkdirSync(outputDir, { recursive: true });
// 官方导出被 1000ms debounce 包裹（Metro watcher 场景用于合并文件事件），
// 同步退出会饿死定时器，必须等待其延迟窗口后再校验产物
typedRoutesModule.regenerateDeclarations(outputDir);
setTimeout(() => {
  const generated = path.join(outputDir, 'router.d.ts');
  if (!fs.existsSync(generated)) {
    console.error(`[typegen-routes] 生成器执行后仍未产出 ${generated}（查看上方生成器自身错误输出）`);
    process.exit(1);
  }
  console.log(`[typegen-routes] OK: ${path.relative(projectRoot, generated)}`);
}, 1200);

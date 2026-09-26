// 轻量验收截图 + 控制台捕获工具（playwright-core 驱动系统 Edge，无需下载 chromium）。
// 用途：区分"工具链/加载问题"与"造型问题"，并把真实控制台打印出来。
//
// 用法：
//   node tools/asset-pipeline/_probe/shot_edge.mjs \
//     --base "http://127.0.0.1:5199/pet-game/index.html?demo=1" \
//     --shots room,front,portrait --outdir ../../shots/v18 --wait 6000
//
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const require = createRequire(HERE + '/x.js')
const { chromium } = require('C:/Users/Administrator/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

// 优先 playwright 自带 chromium（与历史可用截图 shot.mjs 同源，swiftshader 行为一致），
// 回退系统 Edge。
const EDGE = 'C:/Users/Administrator/AppData/Local/ms-playwright/chromium-1234/chrome-win64/chrome.exe'

function parseArgs(argv) {
  const out = {}
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i]
    if (!key.startsWith('--')) continue
    out[key.slice(2)] = argv[i + 1]
    i += 1
  }
  return out
}

const args = parseArgs(process.argv.slice(2))
const base = args.base || 'http://127.0.0.1:5199/pet-game/index.html?demo=1'
const shots = (args.shots || 'room').split(',').map(s => s.trim()).filter(Boolean)
const outdir = resolve(HERE, args.outdir || '../../shots')
const waitMs = Number(args.wait || 6000)
mkdirSync(outdir, { recursive: true })

const browser = await chromium.launch({
  executablePath: EDGE,
  headless: true,
  args: [
    '--no-sandbox',
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--enable-unsafe-swiftshader',
    '--ignore-gpu-blocklist',
    '--enable-webgl',
  ],
})
const context = await browser.newContext({
  viewport: { width: 960, height: 640 },
  deviceScaleFactor: 2,
})
const page = await context.newPage()

const problems = []
const probes = []
page.on('console', (message) => {
  const text = message.text()
  if (text.includes('[pet-probe]')) probes.push(text)
  else if (message.type() === 'error') problems.push('[error] ' + text)
  else if (message.type() === 'warning') problems.push('[warn] ' + text)
})
page.on('pageerror', (err) => problems.push('[pageerror] ' + err.message))
page.on('requestfailed', (req) => problems.push('[reqfail] ' + req.url() + ' :: ' + (req.failure()?.errorText || '')))
page.on('response', (res) => {
  if (res.status() >= 400) problems.push('[http' + res.status() + '] ' + res.url())
})

for (const shot of shots) {
  const url = `${base}&shot=${shot}`
  console.log(`\n=== shot=${shot} url=${url}`)
  await page.goto(url, { waitUntil: 'load', timeout: 30000 }).catch(e => problems.push('[goto] ' + e.message))
  await page.waitForTimeout(waitMs)
  const file = resolve(outdir, `${shot}.png`)
  await page.screenshot({ path: file }).catch(e => problems.push('[screenshot] ' + e.message))
  console.log('  saved', file)
}

console.log('\n========== PROBES ([pet-probe]) ==========')
for (const p of probes) console.log(p)
console.log('\n========== PROBLEMS ==========')
for (const p of problems) console.log(p)
if (!problems.length) console.log('(none)')

await browser.close()

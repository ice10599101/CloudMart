// pet-game 视觉验收截图工具（Playwright → 静态服务器上的构建产物）。
//
// 用途：造型迭代时按固定机位抓图，逐轮对比；同时把页面 console/异常打印出来，
// 用于区分"工具链问题"与"造型问题"。
//
// 用法（先起静态服务器：python -m http.server 5199 --directory CloudMart-ui/public）：
//   node tools/shot.mjs --base "http://127.0.0.1:5199/pet-game/index.html?demo=1&accessory=none" \
//        --shots room,front,q34 --outdir ../../shots/round1 --wait 4200
//
// 说明：宿主浏览器依赖解析到 CloudMart-ui 的 node_modules（playwright 已随宿主安装）。

import { createRequire } from 'node:module'
import { mkdirSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const HOST_PKG = 'D:/Ide/IdeaProjects/CloudMart/CloudMart-ui/package.json'
const require = createRequire(existsSync(HOST_PKG) ? HOST_PKG : import.meta.url)
const { chromium } = require('@playwright/test')

const HERE = dirname(fileURLToPath(import.meta.url))

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
const base = args.base || 'http://127.0.0.1:5199/pet-game/index.html?demo=1&accessory=none'
const shots = (args.shots || 'room').split(',').map(s => s.trim()).filter(Boolean)
const outdir = resolve(HERE, args.outdir || '../../shots')
const waitMs = Number(args.wait || 4200)
const width = Number(args.width || 960)
const height = Number(args.height || 548)

mkdirSync(outdir, { recursive: true })

const browser = await chromium.launch({
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
})
const context = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: 2,
    reducedMotion: 'no-preference',
})
const page = await context.newPage()

const problems = []
const probes = []
page.on('console', (message) => {
    const text = message.text()
    if (text.includes('[pet-probe]')) {
        probes.push(text)
    }
    if (message.type() === 'error' || message.type() === 'warning') {
        problems.push(`[${message.type()}] ${text}`)
    }
})
page.on('pageerror', (error) => problems.push(`[pageerror] ${error.message}`))

for (const shot of shots) {
    const url = `${base}${base.includes('?') ? '&' : '?'}shot=${shot}`
    await page.goto(url, { waitUntil: 'load', timeout: 30000 })
    await page.waitForSelector('canvas', { timeout: 20000 })
    await page.waitForTimeout(waitMs)
    if (args.probe) {
        const info = await page.evaluate(() => {
            const canvas = document.querySelector('canvas')
            return {
                window: [window.innerWidth, window.innerHeight],
                canvasPixels: [canvas.width, canvas.height],
                canvasCss: [canvas.clientWidth, canvas.clientHeight],
                dpr: window.devicePixelRatio,
            }
        })
        console.log(`canvas probe ${shot}:`, JSON.stringify(info))
    }
    if (shot.startsWith('action')) {
        // action-<kind>：等场景稳定后触发一次交互演出，再抓演出中段
        const kind = shot.split('-')[1] || 'feed'
        await page.evaluate((k) => {
            window.postMessage({ source: 'pet-host', type: 'actionResult', action: k, ok: true }, window.location.origin)
        }, kind)
        await page.waitForTimeout(Number(args.actionWait || 700))
    }
    const file = resolve(outdir, `${shot}.png`)
    await page.screenshot({ path: file })
    console.log(`shot ${shot} -> ${file}`)
}

if (probes.length) {
    console.log('\n--- framing probe ---')
    for (const line of probes) console.log(line)
}

if (problems.length) {
    console.log('\n--- page console (error/warning) ---')
    for (const line of problems) console.log(line)
} else {
    console.log('\n--- page console: clean ---')
}

await browser.close()

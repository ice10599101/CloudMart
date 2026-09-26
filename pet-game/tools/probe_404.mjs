// 一次性诊断：抓页面里所有失败请求的 URL + 资产依赖报错
import { createRequire } from 'node:module'
import { existsSync } from 'node:fs'
const require = createRequire(existsSync('D:/Ide/IdeaProjects/CloudMart/CloudMart-ui/package.json')
    ? 'D:/Ide/IdeaProjects/CloudMart/CloudMart-ui/package.json' : import.meta.url)
const { chromium } = require('@playwright/test')

const base = process.argv[2] || 'http://127.0.0.1:5199/pet-game/index.html?demo=1'
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } })
page.on('requestfailed', r => console.log('[reqfail]', r.url(), r.failure()?.errorText))
page.on('response', r => { if (r.status() >= 400) console.log('[http' + r.status() + ']', r.url()) })
page.on('console', m => {
    const t = m.type()
    if (t === 'error' || t === 'warning') console.log('[console.' + t + ']', m.text().slice(0, 300))
})
await page.goto(base, { waitUntil: 'load' })
await page.waitForTimeout(6000)
await browser.close()
console.log('[done]')

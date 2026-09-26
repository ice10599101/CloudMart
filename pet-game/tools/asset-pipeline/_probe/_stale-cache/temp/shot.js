/**
 * 视觉验收截图脚本（临时工具，位于 Cocos temp 目录，不参与构建）
 * 用法：node shot.js CAT,PIG   （可用物种：CAT DOG RABBIT HAMSTER TURTLE PIG FOX PANDA WILD）
 */
const { chromium } = require('D:/Ide/IdeaProjects/CloudMart/CloudMart-ui/node_modules/playwright')

const list = (process.argv[2] || 'CAT').split(',')
const extra = process.argv[3] || ''
const headless = process.env.SHOT_HEADLESS !== '0'

;(async () => {
    const browser = await chromium.launch({
        headless,
        args: [
            '--enable-unsafe-swiftshader',
            '--use-angle=swiftshader',
            '--ignore-gpu-blocklist',
            '--disable-gpu-sandbox',
            '--enable-webgl',
        ],
    })
    for (const sp of list) {
        const page = await browser.newPage({ viewport: { width: 820, height: 1180 }, deviceScaleFactor: 1 })
        const errors = []
        page.on('pageerror', e => errors.push('PAGEERROR ' + e.message))
        page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE ' + m.text()) })
        const url = 'http://localhost:8000/pet-game/index.html?demo=1&species=' + sp + extra
        await page.goto(url, { waitUntil: 'load', timeout: 90000 })
        await page.waitForTimeout(11000)
        const out = 'D:/Ide/IdeaProjects/CloudMart/pet-game/temp/shot-' + sp + '.png'
        await page.screenshot({ path: out })
        console.log('[' + sp + '] saved -> shot-' + sp + '.png')
        console.log('[' + sp + '] errors: ' + (errors.length ? errors.slice(0, 8).join(' || ') : 'none'))
        await page.close()
    }
    await browser.close()
})()

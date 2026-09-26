/** 宿主宠物页截图（临时工具，位于 Cocos temp 目录） */
const { chromium } = require('D:/Ide/IdeaProjects/CloudMart/CloudMart-ui/node_modules/playwright')

;(async () => {
    const browser = await chromium.launch({ headless: true })
    const page = await browser.newPage({ viewport: { width: 430, height: 900 } })
    const errors = []
    page.on('pageerror', e => errors.push('PAGEERROR ' + e.message))
    page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE ' + m.text().slice(0, 120)) })
    await page.goto('http://localhost:8000/pet', { waitUntil: 'load', timeout: 90000 })
    await page.waitForTimeout(15000)
    await page.screenshot({ path: 'D:/Ide/IdeaProjects/CloudMart/pet-game/temp/shot-pet.png' })
    console.log('host errors: ' + (errors.length ? errors.slice(0, 4).join(' || ') : 'none'))
    await browser.close()
})()

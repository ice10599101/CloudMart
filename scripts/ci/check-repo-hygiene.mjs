#!/usr/bin/env node
/**
 * C36 仓库卫生门禁：构建产物/依赖目录/凭据文件不得进入 git 索引。
 *
 * 检查对象是「已被 git 跟踪」的文件（git ls-files），因此本地 target/node_modules
 * 不会误伤；只拦真正被提交进仓库的内容。密钥内容扫描由 gitleaks action 负责，
 * 本脚本只做路径/文件名级别的结构性卫生：
 *   1. 构建产物与依赖目录：node_modules/、Maven target/、前端 dist/build/.expo、
 *      coverage、.gradle、__pycache__、各种压缩备份/tmp 后缀等；
 *      （IDE 编辑器共享配置 .vscode/extensions.json 等不在拦截范围）
 *   2. 凭据载体文件：.env（.example/.template 除外）、*.pem/*.p12/*.jks/*.keystore、
 *      id_rsa*、*.kdbx 等。
 *
 * 用法：node scripts/ci/check-repo-hygiene.mjs
 * 退出码：0 通过；1 发现违规。
 */
import { execSync } from 'node:child_process'

const tracked = execSync('git ls-files', { encoding: 'utf8' }).split('\n').filter(Boolean)

// 路径片段（等价于 ^(dir)/ 或 /(dir)/ 任意层级），全部为构建/依赖/IDE 产物
const FORBIDDEN_SEGMENTS = [
  'node_modules/',
  '/target/', // Maven 编译输出（各 mall-*/target/）
  '/dist/', // CloudMart-ui vite build
  '/build/', // cloudmart-mobile 构建输出
  '/.expo/', // Expo 本地导出/缓存
  'coverage/',
  '/.gradle/',
  '/__pycache__/',
  '/.pytest_cache/',
]

// 文件名后缀/精确名（小写匹配）
const FORBIDDEN_SUFFIXES = [
  '.log',
  '.tmp',
  '.temp',
  '.bak',
  '.orig',
  '.pem',
  '.p12',
  '.pfx',
  '.jks',
  '.keystore',
  '.kdbx',
  '.crt',
  '.key',
]
const FORBIDDEN_BASENAMES = new Set([
  '.env',
  '.env.local',
  '.env.production',
  'id_rsa',
  'id_dsa',
  'id_ecdsa',
  'id_ed25519',
])
// 凭据样例/模板允许入库（不含真实秘密）
const ALLOW_SUFFIXES = ['.example', '.sample', '.template', '.md']

const violations = []

for (const raw of tracked) {
  const path = raw.trim()
  const lower = path.toLowerCase()
  const base = lower.split('/').pop() ?? ''

  const allowed = ALLOW_SUFFIXES.some((s) => lower.endsWith(s))

  if (!allowed) {
    if (FORBIDDEN_SEGMENTS.some((seg) => path.startsWith(seg) || path.includes(seg))) {
      violations.push(`${path}  （构建产物/依赖/IDE 目录不得入库）`)
      continue
    }
    if (FORBIDDEN_BASENAMES.has(base) || FORBIDDEN_SUFFIXES.some((s) => base.endsWith(s))) {
      violations.push(`${path}  （凭据/临时文件不得入库；模板请用 .example 后缀）`)
      continue
    }
    // 私钥文件名变体 id_rsa.xxx / id_ed25519.xxx
    if (base.startsWith('id_rsa') || base.startsWith('id_dsa') || base.startsWith('id_ecdsa') || base.startsWith('id_ed25519')) {
      violations.push(`${path}  （疑似私钥文件）`)
    }
  }
}

if (violations.length > 0) {
  console.error(`::error::仓库卫生检查失败，发现 ${violations.length} 个不应入库的文件：`)
  for (const v of violations) console.error('  - ' + v)
  console.error('\n处理方式：从索引移除（git rm --cached <path>）并加入 .gitignore；')
  console.error('凭据文件请立即轮转，密钥内容扫描另见 gitleaks。')
  process.exit(1)
}

console.log(`仓库卫生检查通过：${tracked.length} 个跟踪文件，无构建产物/依赖/凭据载体。`)

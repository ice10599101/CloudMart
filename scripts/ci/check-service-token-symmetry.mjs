#!/usr/bin/env node
/**
 * T23：网关 StripPrefix 与服务令牌入站/出站对称性门禁（零依赖）。
 *
 * 背景：D15 暴露过一类真实故障——调用方配置了 outbound-scopes，目标服务
 * service-token-paths 未列入该 issuer，调用被 401 且日志只有一条 warn。
 * 这类配置断链不会让编译失败，必须在 CI 静态拦截。
 *
 * 检查：
 *  1. 网关 /api/{svc}/** 路由：目标模块存在；StripPrefix 恰好剥掉 api 与 svc 两段。
 *  2. 每个模块 cloudmart.security.service-id 与模块目录一致。
 *  3. 每个 outbound-scopes 目标模块存在，且其 service-token-paths 至少有一条
 *     issuer 包含调用方、scope 与出站值一致（scope 参与验签，不一致同样 401）。
 *
 * 用法：node scripts/ci/check-service-token-symmetry.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..', '..')

// ==================== 网关 ====================

/**
 * 解析 Spring Cloud Gateway 路由（只覆盖本项目用到的 YAML 子集）。
 * @returns {{id:string,uri:string,paths:string[],strip:number|null}[]}
 */
export function parseGatewayRoutes(text) {
  const routes = []
  let current = null
  for (const rawLine of text.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const idMatch = trimmed.match(/^-\s+id:\s*(\S+)/)
    if (idMatch) {
      if (current) routes.push(current)
      current = { id: idMatch[1], uri: '', paths: [], strip: null }
      continue
    }
    if (!current) continue
    const uri = trimmed.match(/^uri:\s*(\S+)/)
    if (uri) current.uri = uri[1]
    const pathPredicate = trimmed.match(/^-\s*Path=(.+)$/)
    if (pathPredicate) current.paths.push(pathPredicate[1].trim())
    const strip = trimmed.match(/^-\s*StripPrefix=(\d+)/)
    if (strip) current.strip = Number(strip[1])
  }
  if (current) routes.push(current)
  return routes
}

/** `/api/x/**` → {svc:'x', expectedStrip:2} */
export function parseApiPath(pattern) {
  const m = pattern.match(/^\/api\/([^/*]+)\/\*\*$/)
  return m ? { svc: m[1], expectedStrip: 2 } : null
}

// ==================== 模块安全配置 ====================

/**
 * 解析一个模块的 cloudmart.security 配置。
 * @returns {{serviceId:string|null,inbound:{prefix:string,issuers:string[],scope:string|null}[],outbound:{target:string,scope:string}[]}}
 */
export function parseSecurityConfig(text) {
  const result = { serviceId: null, inbound: [], outbound: [] }
  let mode = null
  let baseIndent = 0
  let entry = null

  for (const rawLine of text.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const indent = line.search(/\S/)

    const serviceId = trimmed.match(/^service-id:\s*(\S+)/)
    if (serviceId) {
      result.serviceId = serviceId[1]
      continue
    }

    if (/^service-token-paths:\s*$/.test(trimmed)) {
      mode = 'in'
      baseIndent = indent
      entry = null
      continue
    }
    if (/^outbound-scopes:\s*$/.test(trimmed)) {
      mode = 'out'
      baseIndent = indent
      continue
    }
    if (!mode) continue
    if (indent <= baseIndent) {
      mode = null
      continue
    }

    if (mode === 'in') {
      const prefix = trimmed.match(/^-\s*prefix:\s*(\S+)/)
      if (prefix) {
        entry = { prefix: prefix[1].replace(/^['"]|['"]$/g, ''), issuers: [], scope: null }
        result.inbound.push(entry)
        continue
      }
      const issuers = trimmed.match(/^issuers:\s*\[(.*)\]/)
      if (issuers && entry) {
        entry.issuers = issuers[1]
          .split(',')
          .map((s) => s.trim().replace(/^['"]|['"]$/g, ''))
          .filter(Boolean)
        continue
      }
      const scope = trimmed.match(/^scope:\s*(\S+)/)
      if (scope && entry) entry.scope = scope[1]
      continue
    }

    const out = trimmed.match(/^([a-z][\w-]*):\s*(\S+)/)
    if (out) result.outbound.push({ target: out[1], scope: out[2] })
  }
  return result
}

// ==================== 评估 ====================

/**
 * @param {object} input
 * @param {{id:string,uri:string,paths:string[],strip:number|null}[]} input.routes
 * @param {Set<string>|string[]} input.modules 模块目录名
 * @param {Map<string,object>|Record<string,object>} input.configs service-id → 配置
 */
export function evaluate({ routes, modules, configs }) {
  const errors = []
  const moduleSet = modules instanceof Set ? modules : new Set(modules)
  const getConfig = (name) => (configs instanceof Map ? configs.get(name) : configs[name])

  // 1. 网关路由
  for (const route of routes) {
    const apiPaths = route.paths.map(parseApiPath).filter(Boolean)
    if (!apiPaths.length) continue
    for (const api of apiPaths) {
      const target = route.uri.startsWith('lb://') ? route.uri.slice(5) : null
      if (!target) {
        errors.push(`[网关] 路由 ${route.id} 的 uri 不是 lb://（当前 ${route.uri || '空'}）`)
        continue
      }
      if (!moduleSet.has(target)) {
        errors.push(`[网关] 路由 ${route.id} 目标模块不存在：${target}`)
      }
      if (route.strip !== api.expectedStrip) {
        errors.push(
          `[网关] 路由 ${route.id}（${route.paths.join(',')}）StripPrefix=${route.strip ?? '未配置'}，应为 ${api.expectedStrip}；否则下游收到的路径前缀与 Controller 映射不一致`,
        )
      }
    }
  }

  // 2. service-id 与目录一致
  for (const [name, config] of configs instanceof Map ? configs : Object.entries(configs)) {
    if (!config.serviceId) {
      errors.push(`[服务令牌] ${name} 未配置 cloudmart.security.service-id`)
    } else if (config.serviceId !== name) {
      errors.push(`[服务令牌] ${name} 的 service-id=${config.serviceId} 与模块名不一致（令牌 iss 会写错）`)
    }
  }

  // 3. outbound → 目标 inbound 的 issuer/scope 对称
  for (const [caller, config] of configs instanceof Map ? configs : Object.entries(configs)) {
    for (const { target, scope } of config.outbound) {
      if (!moduleSet.has(target)) {
        errors.push(`[服务令牌] ${caller} outbound-scopes 指向不存在的模块 ${target}`)
        continue
      }
      const targetConfig = getConfig(target)
      if (!targetConfig) {
        errors.push(`[服务令牌] ${caller} → ${target}：目标模块无安全配置`)
        continue
      }
      const withIssuer = targetConfig.inbound.filter((e) => e.issuers.includes(caller))
      if (!withIssuer.length) {
        errors.push(
          `[服务令牌] ${caller} → ${target}：目标 service-token-paths 无 issuer=${caller} 的条目，调用会被 401`,
        )
        continue
      }
      // 同一 issuer 可能有多条（不同前缀不同 scope），按出站 scope 找精确条目
      const match = withIssuer.find((e) => !e.scope || e.scope === scope)
      if (!match) {
        errors.push(
          `[服务令牌] ${caller} → ${target}：出站 scope=${scope} 在目标入站条目（${withIssuer
            .map((e) => `${e.prefix}:${e.scope}`)
            .join('、')}）中无匹配，scope 参与验签会 401`,
        )
      }
    }
  }

  return { errors }
}

// ==================== 主流程 ====================

function listModules() {
  return fs
    .readdirSync(ROOT, { withFileTypes: true })
    .filter((e) => e.isDirectory() && /^mall-/.test(e.name))
    .map((e) => e.name)
}

function main() {
  const modules = listModules()
  const gatewayPath = path.join(ROOT, 'mall-gateway', 'src', 'main', 'resources', 'application.yml')
  if (!fs.existsSync(gatewayPath)) {
    console.error('[网关] 未找到 mall-gateway/application.yml')
    process.exit(1)
  }
  const routes = parseGatewayRoutes(fs.readFileSync(gatewayPath, 'utf-8'))

  const configs = new Map()
  for (const mod of modules) {
    const p = path.join(ROOT, mod, 'src', 'main', 'resources', 'application.yml')
    if (!fs.existsSync(p)) continue
    const config = parseSecurityConfig(fs.readFileSync(p, 'utf-8'))
    if (config.serviceId) configs.set(config.serviceId, config)
  }

  const { errors } = evaluate({ routes, modules, configs })
  if (errors.length) {
    for (const e of errors) console.error(`::error::${e}`)
    console.error(`\n网关/服务令牌对称性检查失败：${errors.length} 项。`)
    process.exit(1)
  }
  console.log(
    `网关/服务令牌对称性检查通过：${routes.length} 条路由，${configs.size} 个模块安全配置，出站依赖全部有对应入站 issuer。`,
  )
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}

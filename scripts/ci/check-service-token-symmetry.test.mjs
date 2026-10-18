import test from 'node:test'
import assert from 'node:assert/strict'

import {
  evaluate,
  parseApiPath,
  parseGatewayRoutes,
  parseSecurityConfig,
} from './check-service-token-symmetry.mjs'

test('网关解析：id/uri/Path/StripPrefix', () => {
  const yml = `
spring:
  cloud:
    gateway:
      routes:
        - id: mall-order
          uri: lb://mall-order
          predicates:
            - Path=/api/order/**
          filters:
            - StripPrefix=2
        - id: mall-ws
          uri: lb://mall-notification
          predicates:
            - Path=/ws/notifications/**
`
  const routes = parseGatewayRoutes(yml)
  assert.equal(routes.length, 2)
  assert.deepEqual(routes[0], { id: 'mall-order', uri: 'lb://mall-order', paths: ['/api/order/**'], strip: 2 })
  assert.equal(routes[1].strip, null)
})

test('API 路径识别：只匹配 /api/{svc}/**', () => {
  assert.deepEqual(parseApiPath('/api/order/**'), { svc: 'order', expectedStrip: 2 })
  assert.equal(parseApiPath('/api/order/list'), null)
  assert.equal(parseApiPath('/ws/notifications/**'), null)
})

test('安全配置解析：service-id、入站 prefix/issuers/scope、出站映射', () => {
  const yml = `
cloudmart:
  security:
    service-id: mall-order
    service-token-paths:
      - prefix: /admin
        issuers: [mall-admin]
        scope: order:admin
      - prefix: /internal
        issuers: [mall-payment, mall-wms]
        scope: order:internal
    outbound-scopes:
      mall-product: product:read
      mall-risk: risk:check
    user-jwt-verification-enabled: true
`
  const config = parseSecurityConfig(yml)
  assert.equal(config.serviceId, 'mall-order')
  assert.deepEqual(config.inbound, [
    { prefix: '/admin', issuers: ['mall-admin'], scope: 'order:admin' },
    { prefix: '/internal', issuers: ['mall-payment', 'mall-wms'], scope: 'order:internal' },
  ])
  assert.deepEqual(config.outbound, [
    { target: 'mall-product', scope: 'product:read' },
    { target: 'mall-risk', scope: 'risk:check' },
  ])
})

const modules = ['mall-admin', 'mall-order', 'mall-product']

test('评估：健康配置无错误', () => {
  const configs = new Map([
    ['mall-admin', { serviceId: 'mall-admin', inbound: [], outbound: [{ target: 'mall-order', scope: 'order:admin' }] }],
    ['mall-order', { serviceId: 'mall-order', inbound: [{ prefix: '/admin', issuers: ['mall-admin'], scope: 'order:admin' }], outbound: [] }],
    ['mall-product', { serviceId: 'mall-product', inbound: [], outbound: [] }],
  ])
  const routes = [{ id: 'mall-order', uri: 'lb://mall-order', paths: ['/api/order/**'], strip: 2 }]
  const { errors } = evaluate({ routes, modules, configs })
  assert.deepEqual(errors, [])
})

test('评估：StripPrefix 缺失/错误、目标模块不存在都会红', () => {
  const routes = [
    { id: 'mall-order', uri: 'lb://mall-order', paths: ['/api/order/**'], strip: 1 },
    { id: 'ghost', uri: 'lb://mall-ghost', paths: ['/api/ghost/**'], strip: 2 },
  ]
  const { errors } = evaluate({ routes, modules, configs: new Map() })
  assert.equal(errors.filter((e) => e.includes('StripPrefix')).length, 1)
  assert.equal(errors.filter((e) => e.includes('目标模块不存在')).length, 1)
})

test('评估：出站目标缺少 issuer 或 scope 不一致都会红', () => {
  const configs = new Map([
    [
      'mall-admin',
      {
        serviceId: 'mall-admin',
        inbound: [],
        outbound: [
          { target: 'mall-order', scope: 'order:admin' },
          { target: 'mall-product', scope: 'product:admin' },
        ],
      },
    ],
    [
      'mall-order',
      { serviceId: 'mall-order', inbound: [{ prefix: '/admin', issuers: ['mall-someone'], scope: 'order:admin' }], outbound: [] },
    ],
    [
      'mall-product',
      { serviceId: 'mall-product', inbound: [{ prefix: '/admin', issuers: ['mall-admin'], scope: 'product:read' }], outbound: [] },
    ],
  ])
  const { errors } = evaluate({ routes: [], modules, configs })
  assert.equal(errors.filter((e) => e.includes('无 issuer=mall-admin')).length, 1)
  assert.equal(errors.filter((e) => e.includes('出站 scope=product:admin')).length, 1)
})

test('评估：service-id 与模块名不一致会红', () => {
  const configs = new Map([
    ['mall-order', { serviceId: 'mall-orders', inbound: [], outbound: [] }],
  ])
  const { errors } = evaluate({ routes: [], modules, configs })
  assert.equal(errors.length, 1)
  assert.match(errors[0], /service-id=mall-orders/)
})

import type { UserConfigExport } from '@tarojs/cli'
import path from 'path'
import fs from 'fs'

// 直读 cloudmart-mobile/.env（与 config/index.ts 的 readLocalEnv 同逻辑，
// 保证 H5 代理 target 与小程序 defineConstants 烘焙值永不分歧）
function readLocalEnv(key: string): string | undefined {
  const envPath = path.resolve(__dirname, '..', '.env')
  if (!fs.existsSync(envPath)) return undefined
  const content = fs.readFileSync(envPath, 'utf-8')
  const match = content.match(new RegExp(`^${key}=(.+)$`, 'm'))
  return match?.[1]?.trim()
}

// H5 dev server proxy target，与小程序保持一致：${TARO_APP_API_HOST}:8090
// 默认 localhost:8090（本机启动 Gateway 时），.env 配置服务器 IP 时自动指向服务器
const API_HOST = process.env.TARO_APP_API_HOST
  || readLocalEnv('TARO_APP_API_HOST')
  || 'http://127.0.0.1'
const GATEWAY_TARGET = `${API_HOST}:8090`
console.log(`[taro config] H5 proxy target = ${GATEWAY_TARGET}`)

export default {
  logger: {
    quiet: false,
    stats: true,
  },
  mini: {},
  h5: {
    devServer: {
      port: 10086,
      proxy: {
        '/api': {
          target: GATEWAY_TARGET,
          changeOrigin: true,
          bypass(req) {
            // Don't proxy Vite module requests (e.g. /api/community.ts)
            if (req.url && /\.(ts|tsx|js|jsx|css|scss|map|wxml|json)$/i.test(req.url)) {
              return req.url
            }
          },
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              // Remove Origin header to avoid CORS check on gateway side
              // since this is a server-side proxy, not a browser cross-origin request
              proxyReq.removeHeader('Origin')
              proxyReq.removeHeader('Referer')
            })
          },
        },
      },
    },
  },
} as UserConfigExport

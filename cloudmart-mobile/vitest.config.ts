import { defineConfig } from 'vitest/config'
import path from 'path'

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  // 本机环境 localhost 无法被 getaddrinfo 解析（EAI_FAIL，禁止改系统 hosts/DNS）；
  // Vite dev server（vitest 复用其 WS/RPC）默认绑定 localhost，这里显式绑回环 IP
  server: {
    host: '127.0.0.1',
    // 测试运行不需要 HMR：hmr:false 仍会创建独立 WS 通道（vite 5 默认 24678/0.0.0.0），
    // ws:false 才返回 noop，避免本机绑定 0.0.0.0 报 ENOTSOCK 噪音
    hmr: false,
    ws: false,
  },
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})

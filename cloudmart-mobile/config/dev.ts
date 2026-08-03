import type { UserConfigExport } from '@tarojs/cli'

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
          target: 'http://localhost:8090',
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

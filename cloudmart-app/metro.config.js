const { getDefaultConfig } = require('expo/metro-config')

const config = getDefaultConfig(__dirname)

// Proxy /api requests to backend gateway in web dev mode
config.server = config.server || {}
config.server.enhanceMiddleware = (middleware) => {
  return (req, res, next) => {
    // Handle CORS preflight
    if (req.method === 'OPTIONS' && req.url && req.url.startsWith('/api/')) {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        'Access-Control-Max-Age': '86400',
      })
      res.end()
      return
    }

    if (req.url && req.url.startsWith('/api/')) {
      const http = require('http')
      // 与 request.ts 原生端共用 EXPO_PUBLIC_API_HOST（默认本机 Gateway）；
      // Gateway 在远程服务器时通过 .env 注入（如 http://129.204.152.168）
      const gatewayHost = process.env.EXPO_PUBLIC_API_HOST || 'http://127.0.0.1'
      const gatewayOrigin = `${gatewayHost.replace(/\/+$/, '')}:8090`
      const targetUrl = `${gatewayOrigin}${req.url}`

      // Build clean headers: remove Origin/Referer to bypass gateway CORS check
      const { origin, referer, ...cleanHeaders } = req.headers
      cleanHeaders.host = gatewayOrigin.replace(/^https?:\/\//, '')

      const proxyReq = http.request(
        targetUrl,
        {
          method: req.method,
          headers: cleanHeaders,
        },
        (proxyRes) => {
          const headers = {
            ...proxyRes.headers,
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type, Authorization',
          }
          res.writeHead(proxyRes.statusCode, headers)
          proxyRes.pipe(res)
        }
      )
      proxyReq.on('error', (err) => {
        res.writeHead(502)
        res.end(`Proxy error: ${err.message}`)
      })
      req.pipe(proxyReq)
    } else {
      middleware(req, res, next)
    }
  }
}

module.exports = config

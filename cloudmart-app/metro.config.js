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
      const targetUrl = `http://127.0.0.1:8090${req.url}`

      // Build clean headers: remove Origin/Referer to bypass gateway CORS check
      const { origin, referer, ...cleanHeaders } = req.headers
      cleanHeaders.host = '127.0.0.1:8090'

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

import express from 'express'
import helmet from 'helmet'
import cors from 'cors'
import rateLimit from 'express-rate-limit'
import { env } from './config/env.js'
import { connectDatabase, disconnectDatabase } from './config/database.js'
import { logger } from './utils/logger.js'
import { AppError } from './utils/errors.js'
import { extractAuthHeader, requireAuth } from './middleware/auth.js'
import { requestLogger } from './middleware/requestLogger.js'
import homeRouter from './routes/home.js'
import productRouter from './routes/product.js'
import orderRouter from './routes/order.js'
import userRouter from './routes/user.js'
import cartRouter from './routes/cart.js'

const app = express()

app.use(helmet())
app.use(cors({
  origin: env.CORS_ORIGIN.split(',').map((o) => o.trim()),
  credentials: true,
}))
app.use(express.json({ limit: '1mb' }))
app.use(requestLogger)
app.use(extractAuthHeader)

const globalLimiter = rateLimit({
  windowMs: 60_000,
  max: 300,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    error: { code: 'RATE_LIMITED', message: 'Too many requests, please try again later' },
  },
})
app.use(globalLimiter)

app.get('/health', (_req, res) => {
  res.json({ success: true, data: { status: 'UP', timestamp: new Date().toISOString() } })
})

app.use('/api/bff/home', homeRouter)
app.use('/api/bff/products', productRouter)
app.use('/api/bff/orders', orderRouter)
app.use('/api/bff/user', requireAuth, userRouter)
app.use('/api/bff/cart', requireAuth, cartRouter)

app.use((_req, res) => {
  res.status(404).json({
    success: false,
    error: { code: 'NOT_FOUND', message: 'Endpoint not found' },
  })
})

app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  if (err instanceof AppError) {
    const response: Record<string, unknown> = {
      success: false,
      error: {
        code: err.code,
        message: err.message,
      },
    }
    if ('details' in err && Array.isArray((err as unknown as { details: unknown }).details)) {
      response.error = {
        ...response.error as Record<string, unknown>,
        details: (err as unknown as { details: unknown }).details,
      }
    }
    res.status(err.statusCode).json(response)
    return
  }

  logger.error({ err }, 'Unhandled error')
  res.status(500).json({
    success: false,
    error: { code: 'INTERNAL_ERROR', message: 'Internal server error' },
  })
})

async function startServer() {
  await connectDatabase()

  const server = app.listen(env.PORT, () => {
    logger.info({ port: env.PORT, env: env.NODE_ENV }, 'BFF server started')
  })

  function gracefulShutdown(signal: string) {
    logger.info({ signal }, 'Shutting down gracefully')
    server.close(async () => {
      await disconnectDatabase()
      logger.info('Server closed')
      process.exit(0)
    })
    setTimeout(() => {
      logger.error('Forced shutdown after timeout')
      process.exit(1)
    }, 10_000)
  }

  process.on('SIGTERM', () => gracefulShutdown('SIGTERM'))
  process.on('SIGINT', () => gracefulShutdown('SIGINT'))
}

startServer().catch((err) => {
  logger.error({ err }, 'Failed to start server')
  process.exit(1)
})

export default app

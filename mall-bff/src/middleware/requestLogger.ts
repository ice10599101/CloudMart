import pinoHttp from 'pino-http'
import { logger } from '../utils/logger.js'
import { v4 as uuidv4 } from 'uuid'
import { Request, Response, NextFunction } from 'express'

const pinoMiddleware = pinoHttp({
  logger,
  genReqId: () => uuidv4(),
  reqCustomProps(req: Request) {
    return {
      userId: (req as unknown as { userId?: string }).userId,
    }
  },
  autoLogging: {
    ignorePaths: ['/health'],
  },
})

export function requestLogger(req: Request, res: Response, next: NextFunction): void {
  const requestId = req.headers['x-request-id'] as string | undefined ?? uuidv4()
  req.headers['x-request-id'] = requestId
  res.setHeader('X-Request-ID', requestId)
  pinoMiddleware(req, res, next)
}

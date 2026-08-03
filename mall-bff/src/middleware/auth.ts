import { Request, Response, NextFunction } from 'express'
import { v4 as uuidv4 } from 'uuid'
import { UnauthorizedError } from '../utils/errors.js'

export function extractAuthHeader(req: Request, _res: Response, next: NextFunction): void {
  const authHeader = req.headers.authorization
  if (!authHeader) {
    req.authToken = null
    next()
    return
  }

  const parts = authHeader.split(' ')
  if (parts.length !== 2 || parts[0] !== 'Bearer') {
    next(new UnauthorizedError('Invalid authorization header format'))
    return
  }

  const token = parts[1]
  const segments = token.split('.')
  if (segments.length !== 3) {
    next(new UnauthorizedError('Invalid JWT structure'))
    return
  }

  req.authToken = token
  next()
}

export function requireAuth(req: Request, _res: Response, next: NextFunction): void {
  if (!req.authToken) {
    next(new UnauthorizedError())
    return
  }
  next()
}

declare global {
  namespace Express {
    interface Request {
      authToken: string | null
    }
  }
}

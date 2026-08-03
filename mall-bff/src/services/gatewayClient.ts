import http from 'node:http'
import { env } from '../config/env.js'
import { logger } from '../utils/logger.js'
import { GatewayError } from '../utils/errors.js'

interface GatewayRequestOptions {
  method: string
  path: string
  authToken?: string | null
  body?: unknown
  params?: Record<string, string>
  headers?: Record<string, string>
}

interface ApiResponse<T> {
  success: boolean
  data: T
  error?: {
    code: string
    message: string
    details?: Array<{ field: string; message: string }>
  }
  meta?: {
    page: number
    pageSize: number
    total: number
  }
}

async function makeRequest<T>(options: GatewayRequestOptions): Promise<ApiResponse<T>> {
  const url = new URL(options.path, env.GATEWAY_URL)
  if (options.params) {
    Object.entries(options.params).forEach(([key, value]) => {
      url.searchParams.set(key, value)
    })
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...options.headers,
  }

  if (options.authToken) {
    headers['Authorization'] = `Bearer ${options.authToken}`
  }

  const body = options.body ? JSON.stringify(options.body) : undefined

  return new Promise<ApiResponse<T>>((resolve, reject) => {
    const req = http.request(url, {
      method: options.method,
      headers,
    }, (res) => {
      const chunks: Buffer[] = []
      res.on('data', (chunk: Buffer) => chunks.push(chunk))
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf-8')
        if (res.statusCode && res.statusCode >= 500) {
          logger.error({ statusCode: res.statusCode, path: options.path }, 'Gateway server error')
          reject(new GatewayError('Upstream service unavailable', res.statusCode))
          return
        }
        if (res.statusCode && res.statusCode >= 400) {
          try {
            const errorResponse = JSON.parse(raw) as ApiResponse<T>
            reject(new GatewayError(
              errorResponse.error?.message ?? 'Gateway request failed',
              res.statusCode,
            ))
          } catch {
            reject(new GatewayError('Gateway returned non-JSON error', res.statusCode))
          }
          return
        }
        try {
          resolve(JSON.parse(raw) as ApiResponse<T>)
        } catch {
          reject(new GatewayError('Invalid JSON from gateway', res.statusCode ?? 502))
        }
      })
    })

    req.on('error', (err) => {
      logger.error({ err, path: options.path }, 'Gateway connection error')
      reject(new GatewayError(err.message, 502))
    })

    req.setTimeout(10_000, () => {
      req.destroy()
      reject(new GatewayError('Gateway request timeout', 504))
    })

    if (body) {
      req.write(body)
    }
    req.end()
  })
}

export const gatewayClient = {
  get<T>(path: string, authToken?: string | null, params?: Record<string, string>): Promise<ApiResponse<T>> {
    return makeRequest<T>({ method: 'GET', path, authToken, params })
  },

  post<T>(path: string, body?: unknown, authToken?: string | null): Promise<ApiResponse<T>> {
    return makeRequest<T>({ method: 'POST', path, authToken, body })
  },

  put<T>(path: string, body?: unknown, authToken?: string | null): Promise<ApiResponse<T>> {
    return makeRequest<T>({ method: 'PUT', path, authToken, body })
  },

  del<T>(path: string, authToken?: string | null): Promise<ApiResponse<T>> {
    return makeRequest<T>({ method: 'DELETE', path, authToken })
  },
}

export type { ApiResponse }

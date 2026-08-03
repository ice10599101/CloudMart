export class AppError extends Error {
  readonly statusCode: number
  readonly code: string

  constructor(message: string, statusCode: number, code: string) {
    super(message)
    this.name = this.constructor.name
    this.statusCode = statusCode
    this.code = code
  }
}

export class NotFoundError extends AppError {
  constructor(resource: string) {
    super(`${resource} not found`, 404, 'NOT_FOUND')
  }
}

export class ValidationError extends AppError {
  readonly details: Array<{ field: string; message: string }>

  constructor(message: string, details: Array<{ field: string; message: string }> = []) {
    super(message, 400, 'VALIDATION_ERROR')
    this.details = details
  }
}

export class GatewayError extends AppError {
  readonly upstreamStatus: number

  constructor(message: string, upstreamStatus: number) {
    super(message, 502, 'GATEWAY_ERROR')
    this.upstreamStatus = upstreamStatus
  }
}

export class UnauthorizedError extends AppError {
  constructor(message = 'Authentication required') {
    super(message, 401, 'UNAUTHORIZED')
  }
}

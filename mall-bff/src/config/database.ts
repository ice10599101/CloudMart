import { PrismaClient } from '@prisma/client'
import { logger } from '../utils/logger.js'

export const prisma = new PrismaClient({
  log: [
    { level: 'query', emit: 'event' },
    { level: 'error', emit: 'stdout' },
    { level: 'warn', emit: 'stdout' },
  ],
})

prisma.$on('query', (e) => {
  logger.debug({ query: e.query, duration: e.duration, params: e.params }, 'Prisma query')
})

export async function connectDatabase(): Promise<void> {
  await prisma.$connect()
  logger.info('Prisma database connected')
}

export async function disconnectDatabase(): Promise<void> {
  await prisma.$disconnect()
  logger.info('Prisma database disconnected')
}

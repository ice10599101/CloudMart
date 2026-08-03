import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/integration-*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'line',
  timeout: 30000,
  use: {
    trace: 'on-first-retry',
    baseURL: 'http://localhost:8090',
  },
  projects: [
    {
      name: 'api-tests',
      use: {},
    },
  ],
})

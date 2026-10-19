import { defineConfig, devices } from '@playwright/test'

/** Installation smoke test against an already running deployment: SMOKE_BASE_URL=http://localhost:8088 npm run test:smoke */
export default defineConfig({
  testDir: './e2e',
  testMatch: 'installation-smoke.spec.ts',
  workers: 1,
  timeout: 120_000,
  use: { baseURL: process.env.SMOKE_BASE_URL ?? 'http://localhost:8088', trace: 'retain-on-failure' },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'] } }],
})

import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests run against the assembled application: the backend boots with a disposable
 * PostgreSQL container (gradle bootTestRun) and the Vite dev server proxies /api to it.
 */
export default defineConfig({
  testDir: './e2e',
  testIgnore: 'installation-smoke.spec.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: [
    {
      command: './gradlew -q bootTestRun',
      cwd: '../backend',
      url: 'http://localhost:8081/actuator/health/readiness',
      timeout: 240_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
    },
  ],
})

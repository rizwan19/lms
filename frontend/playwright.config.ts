import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests run against the assembled application: the backend boots with a disposable
 * PostgreSQL container (spring-boot:test-run) and the Vite dev server proxies /api to it.
 */
export default defineConfig({
  testDir: './e2e',
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
      command: './mvnw -q spring-boot:test-run',
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

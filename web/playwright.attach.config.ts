import { defineConfig, devices } from '@playwright/test'

const browserChannel = process.env.PLAYWRIGHT_BROWSER_CHANNEL ?? 'chrome'
const frontendPort = Number(process.env.PLAYWRIGHT_FRONTEND_PORT ?? '4175')
const frontendHost = process.env.PLAYWRIGHT_FRONTEND_HOST ?? '127.0.0.1'

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: false,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: `http://${frontendHost}:${frontendPort}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        channel: browserChannel,
      },
    },
  ],
})

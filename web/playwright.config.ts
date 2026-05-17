import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

const rootDir = path.dirname(fileURLToPath(import.meta.url))
const browserChannel = process.env.PLAYWRIGHT_BROWSER_CHANNEL ?? 'chrome'
const frontendPort = Number(process.env.PLAYWRIGHT_FRONTEND_PORT ?? '4175')
const backendPort = Number(process.env.PLAYWRIGHT_BACKEND_PORT ?? '8081')

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'line',
  use: {
    baseURL: `http://127.0.0.1:${frontendPort}`,
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
  webServer: [
    {
      command: `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:VITE_API_BASE_URL='http://127.0.0.1:${backendPort}/api'; npm.cmd run dev -- --host 127.0.0.1 --port ${frontendPort}"`,
      cwd: rootDir,
      port: frontendPort,
      reuseExistingServer: false,
      stdout: 'pipe',
      stderr: 'pipe',
      timeout: 120 * 1000,
    },
    {
      command: 'powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\start-backend.ps1',
      cwd: rootDir,
      port: backendPort,
      reuseExistingServer: false,
      stdout: 'pipe',
      stderr: 'pipe',
      timeout: 180 * 1000,
      env: {
        ...process.env,
        BACKEND_PORT: `${backendPort}`,
      },
    },
  ],
  outputDir: path.join(rootDir, 'test-results'),
})

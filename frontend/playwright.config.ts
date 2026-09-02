import { defineConfig } from '@playwright/test';

// See e2e/global-setup.ts - clears orphan rides/stale driver state before the
// suite so auto-dispatch cannot vacuum leftovers onto an online test driver.
const globalSetup = './e2e/global-setup.ts';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  globalSetup,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    headless: true,
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
  ],
  webServer: {
    command: 'npx vite --port 5173 --host',
    port: 5173,
    reuseExistingServer: true,
    timeout: 30_000,
  },
});

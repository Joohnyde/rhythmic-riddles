import { defineConfig, devices } from '@playwright/test';

const FRONTEND_URL = process.env.E2E_FRONTEND_URL ?? 'http://localhost:4200';

function resolveWorkers(): number {
  const configured = process.env.E2E_WORKERS;
  if (configured === undefined) {
    // The E2E specs share one Spring/PostgreSQL stack. Playwright's CPU-based default can
    // overwhelm that shared integration environment on high-core developer machines.
    return 2;
  }

  const workers = Number(configured);
  if (!Number.isInteger(workers) || workers < 1) {
    throw new Error(`E2E_WORKERS must be a positive integer; received: ${configured}`);
  }

  return workers;
}

export default defineConfig({
  testDir: './e2e/specs',
  timeout: 45_000,
  expect: {
    timeout: 12_000,
  },
  fullyParallel: false,
  workers: resolveWorkers(),
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html']],
  use: {
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

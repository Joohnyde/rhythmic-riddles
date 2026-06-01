import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  testMatch: '**/test-catalog-playwright-consistency.spec.ts',
});

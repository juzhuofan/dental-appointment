import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests/ui',
  timeout: 60000,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: '.local/ui-report', open: 'never' }]],
  outputDir: '.local/ui-results',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    channel: 'msedge',
    headless: true,
    viewport: { width: 1440, height: 960 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
});

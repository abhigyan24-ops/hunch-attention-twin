import { defineConfig } from '@vscode/test-cli';

export default defineConfig({
  files: 'test/integration/**/*.test.js',
  version: 'stable',
  workspaceFolder: './test/fixture-workspace',
  mocha: {
    ui: 'bdd',
    timeout: 20000
  }
});

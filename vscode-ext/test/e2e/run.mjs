import { runTests } from '@vscode/test-electron';
import { fileURLToPath } from 'node:url';
await runTests({
 extensionDevelopmentPath: fileURLToPath(new URL('../../', import.meta.url)),
 extensionTestsPath: fileURLToPath(new URL('./activation.cjs', import.meta.url)),
 launchArgs: ['--disable-extensions', '--disable-gpu', '--no-sandbox'],
});

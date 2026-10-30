import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  main: { plugins: [externalizeDepsPlugin()] },
  preload: { plugins: [externalizeDepsPlugin()], build: { rollupOptions: { output: { format: 'cjs', entryFileNames: 'index.cjs' } } } },
  renderer: { root: fileURLToPath(new URL('./src/panel', import.meta.url)), build: { rollupOptions: { input: fileURLToPath(new URL('./src/panel/index.html', import.meta.url)) } } }
});

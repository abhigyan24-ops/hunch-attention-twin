import esbuild from 'esbuild'
import fs from 'node:fs'

await esbuild.build({
  entryPoints: ['src/renderer/overlay/main.ts'],
  bundle: true,
  platform: 'browser',
  format: 'esm',
  outfile: 'out/renderer/overlay/main.js'
})

await esbuild.build({
  entryPoints: ['src/preload/overlay.ts'],
  bundle: true,
  platform: 'node',
  format: 'cjs',
  outfile: 'out/preload/overlay.cjs',
  external: ['electron']
})

fs.mkdirSync('out/renderer/overlay', { recursive: true })
let html = fs.readFileSync('src/renderer/overlay/index.html', 'utf8')
html = html.replace('./main.ts', './main.js')
fs.writeFileSync('out/renderer/overlay/index.html', html, 'utf8')
console.log('Overlay built successfully.')

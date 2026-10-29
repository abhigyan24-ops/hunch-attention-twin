import {build} from 'esbuild';
import {fileURLToPath,pathToFileURL} from 'node:url';
// Reachability probe never prints raw errors, URLs or response bodies.
try{const response=await fetch('http://127.0.0.1:5600/api/0/buckets/',{signal:AbortSignal.timeout(3000)});if(!response.ok)throw Error();await response.body?.cancel();}
catch{console.log('not verified on target: ActivityWatch not running');process.exit(1);}
const entry=fileURLToPath(new URL('./live-aw-smoke-entry.ts',import.meta.url));
const outfile=fileURLToPath(new URL('../out/smoke/live-aw.mjs',import.meta.url));
await build({entryPoints:[entry],outfile,bundle:true,format:'esm',platform:'node'});
const {runSmoke}=await import(pathToFileURL(outfile).href);await runSmoke();

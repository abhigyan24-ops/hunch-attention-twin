import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url)),npm=process.platform==='win32'?'npm.cmd':'npm';
const commands=[['--prefix','relay','test'],['--prefix','laptop','run','typecheck'],['--prefix','laptop','run','build'],['--prefix','laptop','test'],['run','checks'],['run','check:contracts']];
for(const args of commands){const result=process.platform==='win32'?spawnSync(process.env.ComSpec??'cmd.exe',['/d','/c','npm '+args.join(' ')],{cwd:root,stdio:'inherit'}):spawnSync(npm,args,{cwd:root,stdio:'inherit'});if(result.status!==0)process.exit(result.status??1);}
const smoke=spawnSync(process.execPath,[fileURLToPath(new URL('./relay-smoke.mjs',import.meta.url))],{cwd:root,stdio:'inherit'});if(smoke.status!==0)process.exit(smoke.status??1);console.log('check:s8l: PASS (local synthetic peers only; real phone/cloud/TLS not verified on target)');

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createRequire } from 'node:module';

const { extract } = createRequire(import.meta.url)('@electron-internal/extract-zip');
test('Electron installer resolves the same addon-free extractor',()=>{
  const require=createRequire(import.meta.url);
  const electronRequire=createRequire(require.resolve('electron/package.json'));
  assert.equal(electronRequire('@electron-internal/extract-zip').extract,extract);
});
test('installer adapter rejects relative paths', {skip:process.platform!=='win32'}, async()=>{
  await assert.rejects(extract('relative.zip',{dir:'relative'}),/Absolute archive/);
});
test('Windows installer adapter extracts a real archive with literal spaced paths', {skip:process.platform!=='win32'}, async t=>{
  const scratch=await fs.mkdtemp(path.join(os.tmpdir(),'dhyan extractor '));
  t.after(()=>fs.rm(scratch,{recursive:true,force:true}));
  const source=path.join(scratch,"counts' fixture.json"),zip=path.join(scratch,'sample.zip'),destination=path.join(scratch,'output');
  await fs.writeFile(source,'{"count":3}');
  await promisify(execFile)(path.join(process.env.SystemRoot,'System32/WindowsPowerShell/v1.0/powershell.exe'),['-NoLogo','-NoProfile','-NonInteractive','-Command',"$ErrorActionPreference='Stop'; Compress-Archive -LiteralPath $env:DHYAN_SOURCE -DestinationPath $env:DHYAN_ZIP"],{windowsHide:true,env:{...process.env,DHYAN_SOURCE:source,DHYAN_ZIP:zip}});
  await extract(zip,{dir:destination});
  assert.equal(await fs.readFile(path.join(destination,path.basename(source)),'utf8'),'{"count":3}');
});

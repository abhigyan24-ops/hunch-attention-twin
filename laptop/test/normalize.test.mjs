import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {build} from 'esbuild';import {fileURLToPath} from 'node:url';
const bundle=await build({entryPoints:[fileURLToPath(new URL('../src/normalizer/normalize.ts',import.meta.url))],bundle:true,format:'esm',platform:'node',write:false});
const {categorizeProcess,categorizeDomain}=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].text).toString('base64'));
test('Code.exe and code.exe map to coding',()=>{assert.equal(categorizeProcess('Code.exe'),'coding');assert.equal(categorizeProcess('code.exe'),'coding');});
test('unknown processes map to unknown',()=>assert.equal(categorizeProcess('unmapped.exe'),'unknown'));
test('github.com maps to coding',()=>assert.equal(categorizeDomain('github.com'),'coding'));
test('unknown domains map to unknown',()=>assert.equal(categorizeDomain('invalid.example'),'unknown'));
test('canonical SCHEMA_VERSION is 1.1.0',()=>assert.equal(fs.readFileSync(new URL('../../core-spec/SCHEMA_VERSION',import.meta.url),'utf8').trim(),'1.1.0'));

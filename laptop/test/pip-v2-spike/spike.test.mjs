import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import guard from './analyze.cjs';
const stats = mean => ({samples:1,mean,p95:mean,max:mean});
const phase = () => ({elapsedMs:5000,rendererCpuPercent:stats(0),allAppProcessesCpuPercent:stats(0),rendererAnimationFrameIntervalMs:stats(16)});
const fixture = () => ({option:'B',idle:phase(),drag:phase(),ownerProbe:{interactive:true,hoverCallbacks:2,underlayClicks:1}});

test('missing, failed and empty measurement sets never count as a completed trial',()=>{
  for(const value of [null,{}, {status:'NOT_RUN_COMPLETE'}, {...fixture(),drag:null}]) assert.equal(guard.analyze(value).complete,false);
  const value=fixture();value.idle.rendererCpuPercent={samples:0,mean:0,p95:0,max:0};
  assert.equal(guard.analyze(value).complete,false);
});
test('genuine zero CPU is distinct from missing samples; nonfinite samples are rejected',()=>{
  assert.equal(guard.analyze(fixture()).complete,true);
  const value=fixture();value.drag.rendererCpuPercent.mean=NaN;
  assert.equal(guard.analyze(value).complete,false);
});
test('even nonzero owner counters cannot auto-accept Windows behavior or product CPU budget',()=>{
  const result=guard.analyze(fixture());
  assert.equal(result.pointerStatus,'unknown_owner_check_required');
  assert.equal(result.productBudgetStatus,'not_verified_on_target');
});
test('prototype does not import production collectors or use screen capture APIs',()=>{
  const source=['main.cjs','preload.cjs','renderer.cjs'].map(name=>readFileSync(new URL(name,import.meta.url),'utf8')).join('\n');
  assert.doesNotMatch(source,/desktopCapturer|capturePage|captureScreenshot|LaptopPipeline|AwAdapter|active-win/);
});

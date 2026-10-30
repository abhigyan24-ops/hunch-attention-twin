// M0-only isolated prototype. Never imports the production pipeline or user settings.
const {app, BrowserWindow, screen, ipcMain} = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const {performance} = require('node:perf_hooks');
const config = require('./pip-config.proposed.json');
const s = config.spike;
const option = process.argv.includes('--option=B') ? 'B' : 'A';
const interactive = process.argv.includes('--interactive');
const resultArg = process.argv.find(arg => arg.startsWith('--result='));
if (!resultArg) throw Error('A result destination is required');
const resultFile = resultArg.slice('--result='.length);
app.setPath('userData', path.join(path.dirname(resultFile), `profile-${option}`));
const windows = new Map();
const stats = new Map();
const pending = new Map();
let underlay = null;
let underlayClicks = 0;
let displayCount = 0;
let scaleFactors = [];
let exiting = false;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const mean = values => values.length ? values.reduce((a,b) => a+b, 0) / values.length : null;
function summarize(values) {
  const sorted = [...values].sort((a,b) => a-b);
  return {samples: sorted.length, mean: mean(sorted), p95: sorted.length ? sorted[Math.ceil(sorted.length * .95)-1] : null, max: sorted.length ? sorted[sorted.length-1] : null};
}
function safePointerMode(win, capture) {
  if (!win.isDestroyed()) win.setIgnoreMouseEvents(!capture, {forward: true});
}
ipcMain.on('spike-message', (event, message) => {
  const entry = windows.get(event.sender.id);
  if (!entry || event.senderFrame !== event.sender.mainFrame || !message || typeof message !== 'object') return;
  if (message.kind === 'move' && option === 'A') {
    const {workArea: area} = entry.display;
    if (Number.isFinite(message.x) && Number.isFinite(message.y)) {
      entry.window.setBounds({x:Math.round(area.x + Math.max(0, Math.min(area.width-entry.width,message.x))), y:Math.round(area.y + Math.max(0,Math.min(area.height-entry.height,message.y)))});
    }
  } else if (message.kind === 'stats') {
    stats.set(event.sender.id, message);
    pending.get(event.sender.id)?.(message); pending.delete(event.sender.id);
  } else if (message.kind === 'hover') {
    const record = stats.get(event.sender.id) || {};
    record.hoverCount = (record.hoverCount || 0) + 1; stats.set(event.sender.id, record);
  } else if (message.kind === 'capture' && interactive) safePointerMode(entry.window, message.capture === true);
  else if (message.kind === 'heartbeat') entry.heartbeat = performance.now();
});
async function create(display, active) {
  const area=display.workArea;
  const width=option==='B'?area.width:s.smallWindowWidthDip;
  const height=option==='B'?area.height:s.smallWindowHeightDip;
  const win = new BrowserWindow({x:option==='B'?area.x:area.x+Math.max(s.paddingDip,area.width-width-s.travelDip),y:option==='B'?area.y:area.y+Math.max(s.paddingDip,area.height-height),width,height,
    show:false,transparent:true,frame:false,focusable:false,skipTaskbar:true,resizable:false,alwaysOnTop:true,
    webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,backgroundThrottling:true}});
  win.setFocusable(false); win.setAlwaysOnTop(true,'floating'); safePointerMode(win,false);
  const entry={window:win,display,width,height,heartbeat:performance.now()}; windows.set(win.webContents.id,entry);
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate', event => event.preventDefault());
  await win.loadFile(path.join(__dirname,'renderer.html'),{query:{option,active:String(active),interactive:String(interactive),width:String(area.width),height:String(area.height),originX:String(area.x),originY:String(area.y)}});
  win.showInactive();
}
async function phase(name) {
  const frames = [];
  const replies=[];
  for(const [id,entry] of windows) {
    replies.push(new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>{pending.delete(id);reject(Error('Renderer measurement timed out'));},s.phaseMs+s.loadTimeoutMs);
      pending.set(id,message=>{clearTimeout(timeout);frames.push(...message.frameMs);resolve();});
      entry.window.webContents.send('spike-phase',{name,durationMs:s.phaseMs,config:s,heartbeatMs:config.movement.heartbeatMs});
    }));
  }
  app.getAppMetrics(); // Prime: the first call returns zero; do not use it as a sample.
  const rendererCpu=[], totalCpu=[];
  const start=performance.now();
  while(performance.now()-start<s.phaseMs) {
    await sleep(s.sampleMs);
    const ids=new Set([...windows.values()].map(entry=>entry.window.webContents.getOSProcessId()));
    const metrics=app.getAppMetrics();
    rendererCpu.push(metrics.filter(metric=>ids.has(metric.pid)).reduce((sum,metric)=>sum+metric.cpu.percentCPUUsage,0));
    totalCpu.push(metrics.reduce((sum,metric)=>sum+metric.cpu.percentCPUUsage,0));
  }
  await Promise.all(replies);
  return {elapsedMs:performance.now()-start,rendererCpuPercent:summarize(rendererCpu),allAppProcessesCpuPercent:summarize(totalCpu),rendererAnimationFrameIntervalMs:summarize(frames)};
}
app.whenReady().then(async()=>{
  try {
    const displays=screen.getAllDisplays();displayCount=displays.length;scaleFactors=displays.map(display=>display.scaleFactor);
    const primary=screen.getPrimaryDisplay();
    if(option==='A') await create(primary,true);
    else for(const display of displays) await create(display,display.id===primary.id);
    if(interactive) {
      underlay=new BrowserWindow({x:primary.workArea.x+s.paddingDip,y:primary.workArea.y+s.paddingDip,
        width:s.underlayWidthDip,height:s.underlayHeightDip,show:false,focusable:false,alwaysOnTop:false,
        webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true}});
      ipcMain.on('spike-underlay',event=>{if(event.sender===underlay.webContents&&event.senderFrame===event.sender.mainFrame)underlayClicks++;});
      await underlay.loadFile(path.join(__dirname,'underlay.html'));underlay.showInactive();
    }
    const watchdog=setInterval(()=>{for(const entry of windows.values())if(performance.now()-entry.heartbeat>config.movement.watchdogMs)safePointerMode(entry.window,false);},config.movement.heartbeatMs);
    await sleep(s.warmupMs);
    const idle=await phase('idle');const drag=await phase('scripted-drag');
    if(interactive) await sleep(s.ownerProbeMs);
    clearInterval(watchdog);
    const result={option,electronVersion:process.versions.electron,displayCount,scaleFactors,
      protocol:'single-run scripted trajectory; not physical pointer drag or compositor presentation measurement',idle,drag,
      ownerProbe:{interactive,hoverCallbacks:[...stats.values()].reduce((sum,value)=>sum+(value.hoverCount||0),0),underlayClicks,
        hoverForwarding:'UNKNOWN_OWNER_CHECK_REQUIRED',outsideClickThrough:'UNKNOWN_OWNER_CHECK_REQUIRED'}};
    fs.writeFileSync(resultFile,JSON.stringify(result,null,2));
    exiting=true;app.quit();
  } catch {fs.writeFileSync(resultFile,JSON.stringify({option,status:'NOT_RUN_COMPLETE',reason:'spike_failed_or_timed_out'}));exiting=true;app.quit();process.exitCode=1;}
});
app.on('window-all-closed',()=>{if(exiting)app.quit();});
setTimeout(()=>{if(!exiting){fs.writeFileSync(resultFile,JSON.stringify({option,status:'NOT_RUN_COMPLETE',reason:'safety_timeout'}));app.quit();}},s.safetyExitMs).unref();

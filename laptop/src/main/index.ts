import {SyncCoordinator} from '../sync/coordinator';
import {isTrustedUiUrl} from './trustedUi';
import {loadIngestToken} from '../ingest/tokenStore';
import {IngestServer} from '../ingest/IngestServer';
import {FeedRouter} from '../ingest/FeedRouter';
import {saveAllowedExtensionIds} from './settings';
import {app,BrowserWindow,ipcMain,powerMonitor,dialog,Tray,Menu,nativeImage,safeStorage,clipboard,screen,globalShortcut} from 'electron';
import {readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {startLaptopCore} from './laptopCore';
import {PetBridge} from './petBridge';
import {PetPositionController} from './petPosition';
import {declareLocally,type Declaration} from './declaredSession';
import {readObservedToday} from './panelHistory';
import type {UiSnapshot} from '../shared/ui';
import type {PipBehavior} from '../pet/petStateMachine';
export const SCHEMA_VERSION=readFileSync(new URL('../../../core-spec/SCHEMA_VERSION',import.meta.url),'utf8').trim();
let petWindow:BrowserWindow|null=null,panelWindow:BrowserWindow|null=null,tray:Tray|null=null;
let petPosition:PetPositionController|null=null;
let core:Awaited<ReturnType<typeof startLaptopCore>>|undefined,bridge:PetBridge|undefined;
let ingest:IngestServer|undefined,feeds:FeedRouter|undefined,ingestToken:string|null=null,ingestMessage='Extension ingest not started';
let sync:SyncCoordinator|undefined;
let paused=false,history:UiSnapshot['focus']={minutes:null,provenance:'observed',label:'No data: persisted focus snapshots are not available'},historyAt=0,historyRead:Promise<void>|null=null;
const ownsLock=app.requestSingleInstanceLock();if(!ownsLock)app.quit();
// Chromium owns this disposable cache. A per-process directory avoids a stale dev instance locking it.
app.commandLine.appendSwitch('disk-cache-dir',join(tmpdir(),'dhyan-electron-cache',String(process.pid)));
app.commandLine.appendSwitch('disable-gpu-shader-disk-cache');
app.commandLine.appendSwitch('disable-gpu-program-cache');
ipcMain.on('move-pet',(event,dx:unknown,dy:unknown)=>{
 if(!trusted(event)||!petWindow||event.sender!==petWindow.webContents||typeof dx!=='number'||typeof dy!=='number'||!Number.isFinite(dx)||!Number.isFinite(dy)||Math.abs(dx)>200||Math.abs(dy)>200)return;
 petPosition?.move(dx,dy);
});
function ingestSnapshot(){const offline={connected:false,online:false,lastSeenTs:null,lastHeartbeatTs:null};return {available:Boolean(ingest),port:core?.settings.ingest.port??47821,message:ingestMessage,vscode:feeds?.vscode.status()??offline,browser:feeds?.browser.status()??offline,invalid:ingest?.diagnostics['ingest.invalid']??0};}
const rendererFileUrl=new URL('../renderer/index.html',import.meta.url).href;
function trustedUrl(url:string){return isTrustedUiUrl(url,rendererFileUrl,process.env.ELECTRON_RENDERER_URL);}
function trusted(event:Electron.IpcMainEvent|Electron.IpcMainInvokeEvent){return [petWindow,panelWindow].some(window=>window&&event.sender===window.webContents&&event.senderFrame===window.webContents.mainFrame&&trustedUrl(event.senderFrame.url));}
function preferences(){return {preload:fileURLToPath(new URL('../preload/index.cjs',import.meta.url)),contextIsolation:true,nodeIntegration:false,sandbox:true,additionalArguments:[`--schema-version=${SCHEMA_VERSION}`]};}
function load(window:BrowserWindow,panel:boolean){const guard=(event:Electron.Event,url:string)=>{if(!trustedUrl(url))event.preventDefault();};window.webContents.on('will-navigate',guard);window.webContents.on('will-redirect',guard);window.webContents.setWindowOpenHandler(()=>({action:'deny'}));if(process.env.ELECTRON_RENDERER_URL){const url=new URL(process.env.ELECTRON_RENDERER_URL);if(panel)url.searchParams.set('view','panel');void window.loadURL(url.href);}else void window.loadFile(fileURLToPath(new URL('../renderer/index.html',import.meta.url)),panel?{query:{view:'panel'}}:{});}
function openPanel(){if(panelWindow){panelWindow.show();panelWindow.focus();return;}panelWindow=new BrowserWindow({width:620,height:820,show:false,webPreferences:preferences()});panelWindow.on('ready-to-show',()=>panelWindow?.show());panelWindow.on('closed',()=>{panelWindow=null;});load(panelWindow,true);}
function createPet(){petWindow=new BrowserWindow({width:200,height:230,transparent:true,frame:false,alwaysOnTop:true,skipTaskbar:true,resizable:false,focusable:false,show:false,webPreferences:preferences()});petWindow.setAlwaysOnTop(true,'screen-saver');petWindow.setIgnoreMouseEvents(true,{forward:true});petWindow.on('ready-to-show',()=>{if(!paused)petWindow?.showInactive();});petWindow.on('closed',()=>{petWindow=null;});load(petWindow,false);}
async function declare(input:Declaration){if(!core||!bridge)return {ok:false,message:'Collection is unavailable; session was not declared.'};try{const result=await declareLocally(input,core.store,{deviceId:core.settings.deviceId,schemaVersion:SCHEMA_VERSION,nowMs:Date.now()});if(!result)return {ok:false,message:'Choose a valid categorized task and duration.'};bridge.bus.setDeclaredSession(result.session);return {ok:true,message:result.persisted?'Declared locally. Phone sync is pending.':'Declared locally in memory; storage is paused.'};}catch{return {ok:false,message:'Storage is unavailable; session was not declared.'};}}
function updateTray(){if(!tray)return;const items:Electron.MenuItemConstructorOptions[]=[{label:'Declare session',submenu:[{label:'Open session form',click:openPanel},{label:'Focus: coding, 2h, strict',click:()=>{void declare({task:'coding',plannedMin:120,strict:true}).then(result=>{if(!result.ok)dialog.showErrorBox('Dhyan',result.message);});}}]},{label:'Open panel',click:openPanel},{label:paused?'Resume Pip':'Pause Pip',click:()=>{paused=!paused;if(paused)petWindow?.hide();else petWindow?.showInactive();updateTray();}},{label:'Quit',click:()=>app.quit()}];
 if(!app.isPackaged&&process.env.DHYAN_DEV_PET==='1'){const behaviors:PipBehavior[]=['deep_focus','held','drifting','distracted','recovering','fatigued','second_screen_overlap'];items.splice(items.length-1,0,{label:'DEV ONLY: simulate presentation',submenu:[{label:'Use real pipeline',click:()=>bridge?.simulate(null)},...behaviors.map(behavior=>({label:behavior,click:()=>bridge?.simulate(behavior)}))]});}tray.setContextMenu(Menu.buildFromTemplate(items));}
function createTray(){const pixels=Buffer.alloc(16*16*4);for(let i=0;i<256;i++){pixels[i*4]=160;pixels[i*4+1]=110;pixels[i*4+2]=50;pixels[i*4+3]=255;}tray=new Tray(nativeImage.createFromBitmap(pixels,{width:16,height:16}));tray.setToolTip('Dhyan');tray.on('click',openPanel);updateTray();}
ipcMain.on('set-ignore-mouse',(event,ignore:unknown)=>{if(trusted(event)&&petWindow&&event.sender===petWindow.webContents&&typeof ignore==='boolean')petWindow.setIgnoreMouseEvents(ignore,{forward:true});});
ipcMain.on('open-panel',event=>{if(trusted(event))openPanel();});
ipcMain.handle('ui-snapshot',event=>{if(!trusted(event))throw Error('unauthorized');const now=Date.now();if(core&&now-historyAt>=30000&&!historyRead){historyAt=now;historyRead=readObservedToday(app.getPath('userData'),now).then(value=>{history=value;}).catch(()=>{history={minutes:null,provenance:'observed',label:'No data: focus history could not be read'};}).finally(()=>{historyRead=null;});}const value=bridge?.snapshot(history)??{inputs:{local:null,collector:'unknown',remote:null,declaredSession:null,storagePaused:true},pet:{behavior:null,availability:'no_data',label:'No data: collector could not start',heldCount:null,calibration:'rule-based, not calibrated'},focus:history,pairing:{status:'not_paired',label:'Not paired'},diagnostics:{paused:true},ts:now,demo:false};return {...value,ingest:ingestSnapshot(),...(sync?{pairing:sync.pairingSnapshot(),sync:sync.snapshot()}:{})};});
ipcMain.handle('declare-session',(event,input:unknown)=>{if(!trusted(event)||!input||typeof input!=='object'||Array.isArray(input)||Object.keys(input).sort().join(',')!=='plannedMin,strict,task')return {ok:false,message:'Invalid session request.'};return declare(input as Declaration);});
ipcMain.handle('label-process',async(event,process:unknown,category:unknown)=>{if(!trusted(event)||!core||typeof process!=='string'||typeof category!=='string')return false;return core.labels.labelOnce(process,category).catch(()=>false);});
ipcMain.handle('storage-health',event=>trusted(event)?core?.diagnostics??null:null);
ipcMain.handle('ingest-config',event=>{if(!trusted(event))throw Error('unauthorized');return {available:Boolean(ingest),token:ingestToken,port:core?.settings.ingest.port??47821,allowedExtensionIds:[...(core?.settings.ingest.allowedExtensionIds??[])],message:ingestMessage};});
ipcMain.handle('ingest-copy-token',event=>{if(!trusted(event)||!ingestToken)return false;clipboard.writeText(ingestToken);return true;});
let settingsWrite=Promise.resolve();
ipcMain.handle('ingest-save-ids',(event,ids:unknown)=>{if(!trusted(event)||!core||!Array.isArray(ids)||!ids.every(id=>typeof id==='string'&&/^[a-p]{32}$/.test(id)))return false;const operation=settingsWrite.then(async()=>{await saveAllowedExtensionIds(app.getPath('userData'),core!.settings,ids);ingest?.setAllowedExtensionIds(ids);return true;}).catch(()=>false);settingsWrite=operation.then(()=>{});return operation;});
ipcMain.handle('sync-pair',async(event,relay:unknown)=>{if(!trusted(event)||!sync||typeof relay!=='string')return false;return sync.begin(relay).catch(()=>false);});
ipcMain.handle('sync-confirm',(event,matches:unknown,confirmationId:unknown)=>trusted(event)&&sync&&typeof matches==='boolean'&&typeof confirmationId==='string'?sync.confirm(matches,confirmationId):false);
ipcMain.handle('sync-unpair',async event=>trusted(event)&&sync?sync.unpair().catch(()=>false):false);
ipcMain.handle('sync-command',(event,cmd:unknown,args:unknown)=>{if(!trusted(event)||!sync||typeof cmd!=='string'||!args||typeof args!=='object'||Array.isArray(args))return false;return sync.sendCommand(cmd,args as Record<string,unknown>);});
app.on('second-instance',openPanel);
app.whenReady().then(async()=>{if(!ownsLock)return;try{core=await startLaptopCore(app.getPath('userData'),SCHEMA_VERSION,()=>powerMonitor.getSystemIdleTime());bridge=new PetBridge(core.pipeline);feeds=new FeedRouter(core.pipeline);sync=new SyncCoordinator({directory:app.getPath('userData'),deviceId:core.settings.deviceId,schemaVersion:SCHEMA_VERSION,store:core.store,bus:bridge.bus,storage:safeStorage,development:!app.isPackaged,stateMappingApproved:true});await sync.start();
try{ingestToken=await loadIngestToken(app.getPath('userData'),safeStorage);ingest=await IngestServer.start({port:core.settings.ingest.port,token:ingestToken,allowedExtensionIds:core.settings.ingest.allowedExtensionIds,onMessage:(client,message)=>{feeds!.accept(client,message);},onConnection:(client,connected)=>feeds!.connection(client,connected)});ingestMessage='Local ingest is ready';}catch{ingestMessage='Extension ingest unavailable: secure storage or local port needs attention';}}catch{dialog.showErrorBox('Dhyan storage unavailable','Local storage needs recovery. Collection is paused.');}createPet();if(petWindow){petPosition=new PetPositionController(app.getPath('userData'),petWindow);void petPosition.load();screen.on('display-removed',()=>petPosition?.restore());screen.on('display-metrics-changed',()=>petPosition?.restore());globalShortcut.register('Control+Alt+P',()=>{paused=true;petWindow?.hide();updateTray();});}createTray();app.on('activate',openPanel);});
let closing=false,closed=false;app.on('before-quit',event=>{if(!core||closed)return;event.preventDefault();if(closing)return;closing=true;bridge?.stop();void (async()=>{await ingest?.close();await sync?.stop();await settingsWrite;await core!.stop();})().then(()=>{if(core?.store.health.paused)dialog.showErrorBox('Dhyan storage unavailable','Recent events may remain only in memory.');}).catch(()=>dialog.showErrorBox('Dhyan storage unavailable','Some recent events could not be flushed.')).finally(()=>{closed=true;tray?.destroy();app.quit();});});
app.on('window-all-closed',()=>{/* Tray owns lifecycle; closing the panel never stops collection. */});
app.on('will-quit',()=>globalShortcut.unregisterAll());

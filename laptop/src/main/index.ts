import {SyncCoordinator} from '../sync/coordinator';
import {isTrustedUiUrl} from './trustedUi';
import {loadIngestToken} from '../ingest/tokenStore';
import {IngestServer} from '../ingest/IngestServer';
import {FeedRouter} from '../ingest/FeedRouter';
import {saveAllowedExtensionIds} from './settings';
import {SidecarManager} from './sidecar';
import {app,BrowserWindow,ipcMain,powerMonitor,dialog,Tray,Menu,nativeImage,safeStorage,clipboard,screen,globalShortcut} from 'electron';
import {readFileSync,mkdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {startLaptopCore} from './laptopCore';
import {PetBridge} from './petBridge';
import {PetPositionController} from './petPosition';
import {PetReactionController} from './petReaction';
import {declareLocally,type Declaration} from './declaredSession';
import {readObservedToday} from './panelHistory';
import type {UiSnapshot} from '../shared/ui';
import type {PipBehavior} from '../pet/petStateMachine';
export const SCHEMA_VERSION=readFileSync(new URL('../../../core-spec/SCHEMA_VERSION',import.meta.url),'utf8').trim();
let petWindow:BrowserWindow|null=null,panelWindow:BrowserWindow|null=null,tray:Tray|null=null;
let petPosition:PetPositionController|null=null;
let petReaction:PetReactionController|null=null;
let dragActive=false,dragUntil=0,lastCursor:{x:number;y:number}|null=null;let visualStage=0;let hitboxTimer:ReturnType<typeof setInterval>|null=null;
let lastIgnore:boolean|null=null;
let core:Awaited<ReturnType<typeof startLaptopCore>>|undefined,bridge:PetBridge|undefined;
let ingest:IngestServer|undefined,feeds:FeedRouter|undefined,ingestToken:string|null=null,ingestMessage='Extension ingest not started';
let sync:SyncCoordinator|undefined;
let sidecar:SidecarManager|undefined;
let paused=false,history:UiSnapshot['focus']={minutes:null,provenance:'observed',label:'No data: persisted focus snapshots are not available'},historyAt=0,historyRead:Promise<void>|null=null;
if(!app.isPackaged){const devProfile=join(app.getPath('appData'),'Dhyan-PipV2-Isolated');mkdirSync(devProfile,{recursive:true});app.setPath('userData',devProfile);}
const ownsLock=app.requestSingleInstanceLock();if(!ownsLock)app.quit();
// Chromium owns this disposable cache. A per-process directory avoids a stale dev instance locking it.
app.commandLine.appendSwitch('disk-cache-dir',join(tmpdir(),'dhyan-electron-cache',String(process.pid)));
app.commandLine.appendSwitch('disable-gpu-shader-disk-cache');
app.commandLine.appendSwitch('disable-gpu-program-cache');
ipcMain.on('move-pet',(event,dx:unknown,dy:unknown)=>{
 if(!trusted(event)||!petWindow||event.sender!==petWindow.webContents||typeof dx!=='number'||typeof dy!=='number'||!Number.isFinite(dx)||!Number.isFinite(dy)||Math.abs(dx)>200||Math.abs(dy)>200)return;
 petPosition?.move(dx,dy);
 if(dragActive){lastCursor=screen.getCursorScreenPoint();dragUntil=Date.now()+2000;}
});
ipcMain.on('pet-drag-active',(event,active:unknown)=>{
 if(!trusted(event)||event.sender!==petWindow?.webContents||typeof active!=='boolean')return;
 dragActive=active;dragUntil=active?Date.now()+2000:0;lastCursor=active?screen.getCursorScreenPoint():null;
 if(active)petReaction?.dismissIfRecent(Date.now());
});
function updatePetHitbox(){
 if(!petWindow||petWindow.isDestroyed()||!petWindow.isVisible())return;
 const point=screen.getCursorScreenPoint(),b=petWindow.getBounds();
 if(dragActive&&Date.now()>dragUntil){dragActive=false;lastCursor=null;}
 if(dragActive&&lastCursor){petPosition?.move(point.x-lastCursor.x,point.y-lastCursor.y);lastCursor=point;}
 const over=point.x>=b.x+12&&point.x<b.x+182&&point.y>=b.y+12&&point.y<b.y+210;
 const ignore=!(dragActive||over);if(ignore!==lastIgnore){petWindow.setIgnoreMouseEvents(ignore,{forward:true});lastIgnore=ignore;}
}
ipcMain.handle('pet-action',async(event,action:unknown)=>{
 if(!trusted(event)||!petReaction||typeof action!=='string')return false;
 const now=Date.now();
 if(action==='snooze15')petReaction.snoozeUntil=now+15*60000;
 else if(action==='snooze60')petReaction.snoozeUntil=now+60*60000;
 else if(action==='break15')petReaction.breakUntil=now+15*60000;
 else if(action==='dismiss'){petReaction.feedback('dismissed',now);void sidecar?.sendFeedback('dismissed');}
 else if(action==='back'){petReaction.feedback('accepted',now);void sidecar?.sendFeedback('accepted');petPosition?.dock();}
 else if(action==='focus25'){const result=await declare({task:'unknown',plannedMin:25,strict:false});if(result.ok){petReaction.feedback('accepted',now);void sidecar?.sendFeedback('accepted');petPosition?.dock();}return result.ok;}
 else if(action==='toggleCalm')petReaction.calm=!petReaction.calm;
 else if(action==='toggleDnd')petReaction.dnd=!petReaction.dnd;
 else if(action==='resetPosition')petPosition?.reset();
 else return false;
 updateTray();return true;
});
ipcMain.handle('twin-state',async(event)=>{if(!trusted(event))throw Error('unauthorized');return sidecar?.fetchTwinState()??null;});
ipcMain.handle('twin-feedback',async(event,action:unknown,stage:unknown)=>{if(!trusted(event)||typeof action!=='string'||(action!=='accepted'&&action!=='dismissed'))return false;void sidecar?.sendFeedback(action,typeof stage==='number'?stage:2);return true;});
function ingestSnapshot(){const offline={connected:false,online:false,lastSeenTs:null,lastHeartbeatTs:null};return {available:Boolean(ingest),port:core?.settings.ingest.port??47821,message:ingestMessage,vscode:feeds?.vscode.status()??offline,browser:feeds?.browser.status()??offline,invalid:ingest?.diagnostics['ingest.invalid']??0};}
const rendererFileUrl=new URL('../renderer/index.html',import.meta.url).href;
function trustedUrl(url:string){return isTrustedUiUrl(url,rendererFileUrl,process.env.ELECTRON_RENDERER_URL);}
function trusted(event:Electron.IpcMainEvent|Electron.IpcMainInvokeEvent){return [petWindow,panelWindow].some(window=>window&&event.sender===window.webContents&&event.senderFrame===window.webContents.mainFrame&&trustedUrl(event.senderFrame.url));}
function preferences(){return {preload:fileURLToPath(new URL('../preload/index.cjs',import.meta.url)),contextIsolation:true,nodeIntegration:false,sandbox:true,additionalArguments:[`--schema-version=${SCHEMA_VERSION}`]};}
function load(window:BrowserWindow,panel:boolean){const guard=(event:Electron.Event,url:string)=>{if(!trustedUrl(url))event.preventDefault();};window.webContents.on('will-navigate',guard);window.webContents.on('will-redirect',guard);window.webContents.setWindowOpenHandler(()=>({action:'deny'}));if(process.env.ELECTRON_RENDERER_URL){const url=new URL(process.env.ELECTRON_RENDERER_URL);if(panel)url.searchParams.set('view','panel');void window.loadURL(url.href);}else void window.loadFile(fileURLToPath(new URL('../renderer/index.html',import.meta.url)),panel?{query:{view:'panel'}}:{});}
function openPanel(){if(panelWindow){panelWindow.show();panelWindow.focus();return;}panelWindow=new BrowserWindow({width:620,height:820,show:false,webPreferences:preferences()});panelWindow.on('ready-to-show',()=>panelWindow?.show());panelWindow.on('closed',()=>{panelWindow=null;});load(panelWindow,true);}
function createPet(){petWindow=new BrowserWindow({width:200,height:230,transparent:true,frame:false,alwaysOnTop:true,skipTaskbar:true,resizable:false,show:false,webPreferences:preferences()});petWindow.setAlwaysOnTop(true,'floating');petWindow.on('ready-to-show',()=>{if(!paused)petWindow?.show();});petWindow.webContents.on('did-finish-load',()=>{if(!paused)petWindow?.show();});petWindow.on('closed',()=>{petWindow=null;});load(petWindow,false);}
async function declare(input:Declaration){if(!core||!bridge)return {ok:false,message:'Collection is unavailable; session was not declared.'};try{const result=await declareLocally(input,core.store,{deviceId:core.settings.deviceId,schemaVersion:SCHEMA_VERSION,nowMs:Date.now()});if(!result)return {ok:false,message:'Choose a valid categorized task and duration.'};bridge.bus.setDeclaredSession(result.session);return {ok:true,message:result.persisted?'Declared locally. Phone sync is pending.':'Declared locally in memory; storage is paused.'};}catch{return {ok:false,message:'Storage is unavailable; session was not declared.'};}}
function updateTray(){if(!tray)return;const items:Electron.MenuItemConstructorOptions[]=[{label:'Declare session',submenu:[{label:'Open session form',click:openPanel},{label:'Focus: coding, 2h, strict',click:()=>{void declare({task:'coding',plannedMin:120,strict:true}).then(result=>{if(!result.ok)dialog.showErrorBox('Dhyan',result.message);});}}]},{label:'Open panel',click:openPanel},{label:paused?'Resume Pip':'Pause Pip',click:()=>{paused=!paused;if(paused)petWindow?.hide();else petWindow?.showInactive();updateTray();}},{label:'Quit',click:()=>app.quit()}];
 items.splice(items.length-1,0,{label:'Pip controls',submenu:[{label:'Snooze 15 min',click:()=>{if(petReaction)petReaction.snoozeUntil=Date.now()+900000;}},{label:'Snooze 1 hour',click:()=>{if(petReaction)petReaction.snoozeUntil=Date.now()+3600000;}},{label:'Break 15 min',click:()=>{if(petReaction)petReaction.breakUntil=Date.now()+900000;}},{label:'Calm mode',type:'checkbox',checked:petReaction?.calm??false,click:()=>{if(petReaction)petReaction.calm=!petReaction.calm;updateTray();}},{label:'Do not disturb',type:'checkbox',checked:petReaction?.dnd??false,click:()=>{if(petReaction)petReaction.dnd=!petReaction.dnd;updateTray();}},{label:'Lock position',type:'checkbox',checked:petPosition?.locked??false,click:()=>{if(petPosition)petPosition.locked=!petPosition.locked;updateTray();}},{label:'Size',submenu:[80,96,128].map(size=>({label:size===80?'Small':size===96?'Medium':'Large',click:()=>{petWindow?.setSize(size+104,size+134);petPosition?.restore();}}))},{label:'Reset position',click:()=>petPosition?.reset()}]});
 if(!app.isPackaged&&process.env.DHYAN_DEV_PET==='1'){const behaviors:PipBehavior[]=['deep_focus','held','drifting','distracted','recovering','fatigued','second_screen_overlap'];items.splice(items.length-1,0,{label:'DEV ONLY: simulate presentation',submenu:[{label:'Use real pipeline',click:()=>bridge?.simulate(null)},...behaviors.map(behavior=>({label:behavior,click:()=>bridge?.simulate(behavior)}))]});items.splice(items.length-1,0,{label:'DEV ONLY: simulate distraction',submenu:[{label:'Use real evidence',click:()=>petReaction?.simulate(null)},...[1,2,3,4].map(level=>({label:`Stage ${level}`,click:()=>petReaction?.simulate(level as 1|2|3|4)}))]});}tray.setContextMenu(Menu.buildFromTemplate(items));}
function createTray(){const pixels=Buffer.alloc(16*16*4);for(let i=0;i<256;i++){pixels[i*4]=160;pixels[i*4+1]=110;pixels[i*4+2]=50;pixels[i*4+3]=255;}tray=new Tray(nativeImage.createFromBitmap(pixels,{width:16,height:16}));tray.setToolTip('Dhyan');tray.on('click',openPanel);updateTray();}
ipcMain.on('set-ignore-mouse',(event,ignore:unknown)=>{if(trusted(event)&&petWindow&&event.sender===petWindow.webContents&&typeof ignore==='boolean')petWindow.setIgnoreMouseEvents(dragActive?false:ignore,{forward:true});});
ipcMain.on('open-panel',event=>{if(trusted(event))openPanel();});
ipcMain.handle('ui-snapshot',event=>{if(!trusted(event))throw Error('unauthorized');const now=Date.now();if(core&&now-historyAt>=30000&&!historyRead){historyAt=now;historyRead=readObservedToday(app.getPath('userData'),now).then(value=>{history=value;}).catch(()=>{history={minutes:null,provenance:'observed',label:'No data: focus history could not be read'};}).finally(()=>{historyRead=null;});}const value=bridge?.snapshot(history)??{inputs:{local:null,collector:'unknown',remote:null,declaredSession:null,storagePaused:true},pet:{behavior:null,availability:'no_data',label:'No data: collector could not start',heldCount:null,calibration:'rule-based, not calibrated'},focus:history,pairing:{status:'not_paired',label:'Not paired'},diagnostics:{paused:true},ts:now,demo:false};const reaction=bridge&&petReaction&&!value.demo?petReaction.snapshot(value.inputs,now):undefined;if(reaction&&petWindow){const cursor=screen.getCursorScreenPoint(),b=petWindow.getBounds();reaction.gaze={x:Math.sign(cursor.x-b.x-b.width/2),y:Math.sign(cursor.y-b.y-b.height/2)};}if(reaction&&petWindow&&petPosition&&reaction.level!==visualStage){visualStage=reaction.level;const cursor=screen.getCursorScreenPoint(),display=screen.getDisplayNearestPoint(cursor),a=display.workArea,b=petWindow.getBounds();const edge=cursor.x<a.x+a.width/2?a.x+8:a.x+a.width-b.width-8;if(reaction.level>=3&&reaction.motion)petPosition.springTo(a.x+(a.width-b.width)/2,Math.max(a.y+8,Math.min(a.y+a.height-b.height-8,cursor.y)));else if(reaction.level===2&&!petReaction?.calm)petPosition.springTo(edge+(edge<a.x+a.width/2?70:-70),b.y);else if(reaction.level===0)petPosition.dock();}return {...value,reaction,demo:value.demo||reaction?.simulated===true,ingest:ingestSnapshot(),...(sync?{pairing:sync.pairingSnapshot(),sync:sync.snapshot()}:{})};});
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
app.whenReady().then(async()=>{if(!ownsLock)return;try{core=await startLaptopCore(app.getPath('userData'),SCHEMA_VERSION,()=>powerMonitor.getSystemIdleTime());try{sidecar=new SidecarManager(join(app.getAppPath(),'..'));void sidecar.start();core.pipeline.onEvent(event=>sidecar?.queueEvent(event));}catch{}bridge=new PetBridge(core.pipeline);petReaction=new PetReactionController(core.pipeline);if(!app.isPackaged&&process.env.DHYAN_DEV_PET_LEVEL){const level=Number(process.env.DHYAN_DEV_PET_LEVEL);if([1,2,3,4].includes(level))petReaction.simulate(level as 1|2|3|4);}feeds=new FeedRouter(core.pipeline);sync=new SyncCoordinator({directory:app.getPath('userData'),deviceId:core.settings.deviceId,schemaVersion:SCHEMA_VERSION,store:core.store,bus:bridge.bus,storage:safeStorage,development:!app.isPackaged,stateMappingApproved:true});await sync.start();
try{ingestToken=await loadIngestToken(app.getPath('userData'),safeStorage);ingest=await IngestServer.start({port:core.settings.ingest.port,token:ingestToken,allowedExtensionIds:core.settings.ingest.allowedExtensionIds,onMessage:(client,message)=>{feeds!.accept(client,message);},onConnection:(client,connected)=>feeds!.connection(client,connected)});ingestMessage='Local ingest is ready';}catch{ingestMessage='Extension ingest unavailable: secure storage or local port needs attention';}}catch{dialog.showErrorBox('Dhyan storage unavailable','Local storage needs recovery. Collection is paused.');}createPet();lastIgnore=true;hitboxTimer=setInterval(updatePetHitbox,16);if(petWindow){petPosition=new PetPositionController(app.getPath('userData'),petWindow);void petPosition.load();screen.on('display-removed',()=>petPosition?.restore());screen.on('display-metrics-changed',()=>petPosition?.restore());const togglePet=()=>{paused=!paused;if(paused)petWindow?.hide();else petWindow?.show();updateTray();};globalShortcut.register('CommandOrControl+Alt+P',togglePet);globalShortcut.register('Control+Alt+P',togglePet);}createTray();app.on('activate',openPanel);});
let closing=false,closed=false;app.on('before-quit',event=>{if(!core||closed)return;event.preventDefault();if(closing)return;closing=true;bridge?.stop();petReaction?.stop();void (async()=>{await ingest?.close();await sync?.stop();await settingsWrite;await core!.stop();await sidecar?.stop();})().then(()=>{if(core?.store.health.paused)dialog.showErrorBox('Dhyan storage unavailable','Recent events may remain only in memory.');}).catch(()=>dialog.showErrorBox('Dhyan storage unavailable','Some recent events could not be flushed.')).finally(()=>{closed=true;tray?.destroy();app.quit();});});
app.on('window-all-closed',()=>{/* Tray owns lifecycle; closing the panel never stops collection. */});
app.on('will-quit',()=>{if(hitboxTimer)clearInterval(hitboxTimer);globalShortcut.unregisterAll();});

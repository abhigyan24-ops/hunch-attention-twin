import { app, BrowserWindow, ipcMain, powerMonitor, dialog } from 'electron';
import {startLaptopCore} from './laptopCore';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
export const SCHEMA_VERSION = readFileSync(new URL('../../../core-spec/SCHEMA_VERSION',import.meta.url),'utf8').trim();
let petWindow: BrowserWindow | null = null;
let core:Awaited<ReturnType<typeof startLaptopCore>>|undefined;
if(!app.requestSingleInstanceLock())app.quit();
app.on('second-instance',()=>{petWindow?.show();petWindow?.focus();});
function trusted(event:Electron.IpcMainInvokeEvent){return petWindow && event.sender===petWindow.webContents && event.senderFrame===petWindow.webContents.mainFrame;}
ipcMain.handle('storage-health',event=>trusted(event)?{...core?.store.health,...core?.diagnostics}:null);
ipcMain.handle('label-process',async(event,process:unknown,category:unknown)=>{if(!trusted(event)||typeof process!=='string'||typeof category!=='string'||!core)return false;return core.labels.labelOnce(process,category).catch(()=>false);});
function createWindow(): void {
 petWindow = new BrowserWindow({width:440,height:480,transparent:true,frame:false,alwaysOnTop:true,skipTaskbar:true,resizable:false,webPreferences:{preload:fileURLToPath(new URL('../preload/index.cjs',import.meta.url)),contextIsolation:true,nodeIntegration:false,sandbox:true,additionalArguments:[`--schema-version=${SCHEMA_VERSION}`]}});
 petWindow.setAlwaysOnTop(true,'screen-saver');
 petWindow.setIgnoreMouseEvents(true,{forward:true});
 petWindow.on('closed',()=>{petWindow=null;});
 if(process.env.ELECTRON_RENDERER_URL) void petWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
 else void petWindow.loadFile(fileURLToPath(new URL('../renderer/index.html',import.meta.url)));
}
ipcMain.on('set-ignore-mouse',(event,ignore:unknown)=>{
 if(petWindow && event.sender===petWindow.webContents && event.senderFrame===petWindow.webContents.mainFrame && typeof ignore==='boolean') petWindow.setIgnoreMouseEvents(ignore,{forward:true});
});
app.whenReady().then(async()=>{try{core=await startLaptopCore(app.getPath('userData'),SCHEMA_VERSION,()=>powerMonitor.getSystemIdleTime());}catch{dialog.showErrorBox('Dhyan storage unavailable','Local storage needs recovery. Collection is paused.');}createWindow();app.on('activate',()=>{if(!petWindow)createWindow();});});
let shutdownStarted=false;
let shutdownFinished=false;
app.on('before-quit',event=>{if(!core||shutdownFinished)return;event.preventDefault();if(shutdownStarted)return;shutdownStarted=true;void core.stop().then(()=>{if(core?.store.health.paused)dialog.showErrorBox('Dhyan storage unavailable','Persistence is paused; some recent events remain only in memory.');}).catch(()=>{dialog.showErrorBox('Dhyan storage unavailable','Some recent events could not be flushed.');}).finally(()=>{shutdownFinished=true;app.quit();});});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});

import { app, BrowserWindow, ipcMain } from 'electron';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
export const SCHEMA_VERSION = readFileSync(new URL('../../../core-spec/SCHEMA_VERSION',import.meta.url),'utf8').trim();
let petWindow: BrowserWindow | null = null;
function createWindow(): void {
 petWindow = new BrowserWindow({width:420,height:220,transparent:true,frame:false,alwaysOnTop:true,skipTaskbar:true,resizable:false,webPreferences:{preload:fileURLToPath(new URL('../preload/index.cjs',import.meta.url)),contextIsolation:true,nodeIntegration:false,sandbox:true,additionalArguments:[`--schema-version=${SCHEMA_VERSION}`]}});
 petWindow.setAlwaysOnTop(true,'screen-saver');
 petWindow.setIgnoreMouseEvents(true,{forward:true});
 petWindow.on('closed',()=>{petWindow=null;});
 if(process.env.ELECTRON_RENDERER_URL) void petWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
 else void petWindow.loadFile(fileURLToPath(new URL('../renderer/index.html',import.meta.url)));
}
ipcMain.on('set-ignore-mouse',(event,ignore:unknown)=>{
 if(petWindow && event.sender===petWindow.webContents && event.senderFrame===petWindow.webContents.mainFrame && typeof ignore==='boolean') petWindow.setIgnoreMouseEvents(ignore,{forward:true});
});
app.whenReady().then(()=>{createWindow();app.on('activate',()=>{if(!petWindow)createWindow();});});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});

import { contextBridge, ipcRenderer } from 'electron';
const schemaVersion=process.argv.find(arg=>arg.startsWith('--schema-version='))?.slice('--schema-version='.length) ?? 'unavailable';
contextBridge.exposeInMainWorld('dhyan',{schemaVersion,setIgnoreMouseEvents:(ignore:boolean)=>{if(typeof ignore==='boolean')ipcRenderer.send('set-ignore-mouse',ignore);}});

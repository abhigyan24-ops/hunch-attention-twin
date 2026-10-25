import { contextBridge, ipcRenderer } from 'electron';
const schemaVersion=process.argv.find(arg=>arg.startsWith('--schema-version='))?.slice('--schema-version='.length) ?? 'unavailable';
contextBridge.exposeInMainWorld('dhyan',{schemaVersion,getStorageHealth:()=>ipcRenderer.invoke('storage-health'),labelProcess:(process:string,category:string)=>ipcRenderer.invoke('label-process',process,category),setIgnoreMouseEvents:(ignore:boolean)=>{if(typeof ignore==='boolean')ipcRenderer.send('set-ignore-mouse',ignore);}});


import {contextBridge,ipcRenderer} from 'electron';
import type {DhyanUi} from '../shared/ui';
const api:DhyanUi={schemaVersion:process.argv.find(arg=>arg.startsWith('--schema-version='))?.slice('--schema-version='.length)??'unavailable',snapshot:()=>ipcRenderer.invoke('ui-snapshot'),declareSession:input=>ipcRenderer.invoke('declare-session',input),labelProcess:(process,category)=>ipcRenderer.invoke('label-process',process,category),openPanel:()=>ipcRenderer.send('open-panel'),setIgnoreMouseEvents:ignore=>{if(typeof ignore==='boolean')ipcRenderer.send('set-ignore-mouse',ignore);}};
contextBridge.exposeInMainWorld('dhyan',api);

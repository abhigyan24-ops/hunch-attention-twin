const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('spike',{
  send:message=>ipcRenderer.send('spike-message',message),
  onPhase:callback=>ipcRenderer.on('spike-phase',(_event,message)=>callback(message)),
  underlay:()=>ipcRenderer.send('spike-underlay')
});

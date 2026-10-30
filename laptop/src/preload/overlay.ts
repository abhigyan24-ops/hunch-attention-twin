import { contextBridge, ipcRenderer } from 'electron'
import type { PetDirective, PetBridgeApi } from '../shared/types'

const bridge: PetBridgeApi = {
  onDirective: (cb: (d: PetDirective) => void) => {
    const handler = (_event: Electron.IpcRendererEvent, directive: PetDirective) => cb(directive)
    ipcRenderer.on('pet:directive', handler)
    return () => {
      ipcRenderer.removeListener('pet:directive', handler)
    }
  },
  setIgnoreMouse: (ignore: boolean) => {
    ipcRenderer.send('pet:ignore-mouse', ignore)
  },
  nudgeAccepted: () => {
    ipcRenderer.send('pet:nudge-accepted')
  },
  nudgeDismissed: (reason: 'button' | 'flung' | 'timeout') => {
    ipcRenderer.send('pet:nudge-dismissed', reason)
  },
  petBooped: () => {
    ipcRenderer.send('pet:booped')
  }
}

contextBridge.exposeInMainWorld('petBridge', bridge)

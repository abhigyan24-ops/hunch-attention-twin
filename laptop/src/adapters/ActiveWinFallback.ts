import {execFile} from 'node:child_process';
import type {LaptopAdapter,RawLaptopEvent} from './LaptopAdapter';
const command="Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class DhyanWin { [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow(); [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint p); }'; [uint32]$pidValue=0; [void][DhyanWin]::GetWindowThreadProcessId([DhyanWin]::GetForegroundWindow(),[ref]$pidValue); (Get-Process -Id $pidValue -ErrorAction Stop).ProcessName";
export function foregroundProcess():Promise<string>{return new Promise((resolve,reject)=>execFile('powershell.exe',['-NoProfile','-NonInteractive','-Command',command],{windowsHide:true,timeout:4000,maxBuffer:1024},(error,stdout)=>{if(error)return reject(Error('foreground_unavailable'));const processName=stdout.trim();if(!/^[\w .-]{1,100}$/.test(processName))return reject(Error('foreground_invalid'));resolve(processName.endsWith('.exe')?processName:processName+'.exe');}));}
export class ActiveWinFallback implements LaptopAdapter {
 private callback:(event:RawLaptopEvent)=>void=()=>{};private timer:ReturnType<typeof setInterval>|undefined;private busy=false;private stopped=false;
 constructor(private idle:()=>number,private processName=foregroundProcess,private now=Date.now,private onFailure:()=>void=()=>{}){}
 onRaw(callback:(event:RawLaptopEvent)=>void){this.callback=callback;}
 start(){if(this.timer)return;this.stopped=false;void this.poll();this.timer=setInterval(()=>void this.poll(),5000);}
 stop(){this.stopped=true;if(this.timer)clearInterval(this.timer);this.timer=undefined;}
 async poll(){if(this.busy)return;this.busy=true;try{const process=await this.processName();const idleSec=this.idle();if(!this.stopped&&Number.isFinite(idleSec)&&idleSec>=0){this.callback({kind:'process',ts:this.now(),process});this.callback({kind:'idle',ts:this.now(),idleSec});}}catch{if(!this.stopped)try{this.onFailure();}catch{/* diagnostics stay isolated */}}finally{this.busy=false;}}
}

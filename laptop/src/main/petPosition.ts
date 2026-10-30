import {readFile,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import type {BrowserWindow} from 'electron';
import {screen} from 'electron';
import {restorePosition,savePosition,type PetPosition} from '../pet/positionStore';
import {springStep} from '../pet/positionStore';

/** Only normalized geometry is stored; never foreground window data. */
export class PetPositionController {
 locked=false;
 private file:string;
 private saved:PetPosition|null=null;
 private write=Promise.resolve();
 private animation:ReturnType<typeof setInterval>|null=null;
 private velocityX=0;private velocityY=0;private lastFrame=0;
 constructor(directory:string,private window:BrowserWindow){this.file=join(directory,'pip-position.json');}
 async load(){try{this.saved=JSON.parse(await readFile(this.file,'utf8')) as PetPosition;}catch{this.saved=null;}this.restore();}
 restore(){const size=this.window.getBounds();const displays=screen.getAllDisplays().map(d=>({...d.workArea,id:d.id}));const next=restorePosition(this.saved,displays,size);if(next)this.window.setPosition(next.x,next.y);}
 move(dx:number,dy:number){if(this.locked)return;this.stopAnimation();const b=this.window.getBounds(),point={x:b.x+Math.round(dx),y:b.y+Math.round(dy)};const display=screen.getDisplayNearestPoint(point),a=display.workArea;
  const x=Math.round(Math.max(a.x,Math.min(a.x+a.width-b.width,point.x))),y=Math.round(Math.max(a.y,Math.min(a.y+a.height-b.height,point.y)));
  this.window.setPosition(x,y);this.saved=savePosition(x,y,{...a,id:display.id},b);this.write=this.write.then(()=>writeFile(this.file,JSON.stringify(this.saved),'utf8')).then(()=>{},()=>{});
 }
 springTo(x:number,y:number){if(this.locked)return;this.stopAnimation();this.velocityX=0;this.velocityY=0;this.lastFrame=performance.now();
  this.animation=setInterval(()=>{if(this.window.isDestroyed()){this.stopAnimation();return;}const now=performance.now(),dt=Math.min(.033,(now-this.lastFrame)/1000);this.lastFrame=now;const b=this.window.getBounds();
   const nx=springStep(b.x,this.velocityX,x,12,dt),ny=springStep(b.y,this.velocityY,y,12,dt);this.velocityX=nx.velocity;this.velocityY=ny.velocity;
   const dx=Math.round(nx.position)-b.x,dy=Math.round(ny.position)-b.y;
   if(Math.abs(x-b.x)<1&&Math.abs(y-b.y)<1&&Math.abs(this.velocityX)+Math.abs(this.velocityY)<10){this.stopAnimation();this.move(x-b.x,y-b.y);return;}
   if(dx||dy){const display=screen.getDisplayNearestPoint({x:b.x+dx,y:b.y+dy}),a=display.workArea;this.window.setPosition(Math.round(Math.max(a.x,Math.min(a.x+a.width-b.width,b.x+dx))),Math.round(Math.max(a.y,Math.min(a.y+a.height-b.height,b.y+dy))));}
  },16);
 }
 dock(){const b=this.window.getBounds(),display=screen.getDisplayNearestPoint({x:b.x,y:b.y}),a=display.workArea;
  this.springTo(b.x<a.x+a.width/2?a.x+8:a.x+a.width-b.width-8,Math.max(a.y+8,Math.min(a.y+a.height-b.height-8,b.y)));
 }
 stopAnimation(){if(this.animation)clearInterval(this.animation);this.animation=null;}
 reset(){this.saved=null;this.restore();}
 async flush(){this.stopAnimation();await this.write;}
}

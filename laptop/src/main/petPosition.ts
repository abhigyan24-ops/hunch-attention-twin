import {readFile,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import type {BrowserWindow} from 'electron';
import {screen} from 'electron';
import {restorePosition,savePosition,type PetPosition} from '../pet/positionStore';

/** Only normalized geometry is stored; never foreground window data. */
export class PetPositionController {
 locked=false;
 private file:string;
 private saved:PetPosition|null=null;
 private write=Promise.resolve();
 constructor(directory:string,private window:BrowserWindow){this.file=join(directory,'pip-position.json');}
 async load(){try{this.saved=JSON.parse(await readFile(this.file,'utf8')) as PetPosition;}catch{this.saved=null;}this.restore();}
 restore(){const size=this.window.getBounds();const displays=screen.getAllDisplays().map(d=>({...d.workArea,id:d.id}));const next=restorePosition(this.saved,displays,size);if(next)this.window.setPosition(next.x,next.y);}
 move(dx:number,dy:number){if(this.locked)return;const b=this.window.getBounds(),point={x:b.x+Math.round(dx),y:b.y+Math.round(dy)};const display=screen.getDisplayNearestPoint(point),a=display.workArea;
  const x=Math.round(Math.max(a.x,Math.min(a.x+a.width-b.width,point.x))),y=Math.round(Math.max(a.y,Math.min(a.y+a.height-b.height,point.y)));
  this.window.setPosition(x,y);this.saved=savePosition(x,y,{...a,id:display.id},b);this.write=this.write.then(()=>writeFile(this.file,JSON.stringify(this.saved),'utf8')).then(()=>{},()=>{});
 }
 reset(){this.saved=null;this.restore();}
 async flush(){await this.write;}
}

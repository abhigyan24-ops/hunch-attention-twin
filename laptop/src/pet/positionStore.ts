/** Pure geometry and persistence codec. All coordinates are display-independent DIP. */
export interface WorkArea {x:number;y:number;width:number;height:number;id:number}
export interface PetPosition {displayId:number;relX:number;relY:number;anchor:'free'|'left'|'right'|'top'|'bottom'}
const finite=(n:unknown):n is number=>typeof n==='number'&&Number.isFinite(n);
const clamp=(value:number,min:number,max:number)=>Math.min(Math.max(value,min),Math.max(min,max));
export function restorePosition(value:unknown,displays:readonly WorkArea[],size:{width:number;height:number}):{x:number;y:number;displayId:number}|null {
 if(!displays.length||!finite(size.width)||!finite(size.height)||size.width<=0||size.height<=0)return null;
 const input=value&&typeof value==='object'&&!Array.isArray(value)?value as Partial<PetPosition>:null;
 const valid=input&&finite(input.displayId)&&finite(input.relX)&&finite(input.relY)&&input.relX>=0&&input.relX<=1&&input.relY>=0&&input.relY<=1;
 const display=displays.find(d=>valid&&d.id===input!.displayId)??displays[0];
 const fallback=!valid||display.id!==input!.displayId;
 return {displayId:display.id,x:Math.round(clamp(display.x+(fallback?1:input!.relX!)*(display.width-size.width),display.x,display.x+display.width-size.width)),y:Math.round(clamp(display.y+(fallback?1:input!.relY!)*(display.height-size.height),display.y,display.y+display.height-size.height))};
}
export function savePosition(x:number,y:number,display:WorkArea,size:{width:number;height:number}):PetPosition {
 const spanX=Math.max(1,display.width-size.width),spanY=Math.max(1,display.height-size.height);
 const relX=clamp((x-display.x)/spanX,0,1),relY=clamp((y-display.y)/spanY,0,1);
 const edge=Math.min(relX,1-relX,relY,1-relY);
 const anchor:PetPosition['anchor']=edge>.1?'free':edge===relX?'left':edge===1-relX?'right':edge===relY?'top':'bottom';
 return {displayId:display.id,relX,relY,anchor};
}
export function springStep(position:number,velocity:number,target:number,omega:number,dt:number){
 const step=clamp(dt,0,.033),w=Math.max(0,omega);
 const displacement=position-target,decay=Math.exp(-w*step),next=(displacement+(velocity+w*displacement)*step)*decay;
 return {position:target+next,velocity:(velocity-w*(velocity+w*displacement)*step)*decay};
}
export function dragVelocity(samples:readonly {x:number;y:number;ts:number}[]){
 const end=samples.at(-1),start=samples.find(s=>end&&end.ts-s.ts<=80);if(!start||!end||end.ts<=start.ts)return {vx:0,vy:0};
 const seconds=(end.ts-start.ts)/1000;return {vx:clamp((end.x-start.x)/seconds,-2000,2000),vy:clamp((end.y-start.y)/seconds,-2000,2000)};
}
export function decayVelocity(v:number,dt:number,friction=6){return v*Math.exp(-Math.max(0,friction)*clamp(dt,0,.033));}

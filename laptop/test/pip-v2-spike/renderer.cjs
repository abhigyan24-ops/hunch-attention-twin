const query=new URLSearchParams(location.search);
const sprite=document.getElementById('sprite');
const option=query.get('option');const active=query.get('active')==='true';
const interactive=query.get('interactive')==='true';
const width=Number(query.get('width')),height=Number(query.get('height'));
let config=null,hoverCount=0,dragging=false,offset={x:0,y:0};
let heartbeatTimer=null;
sprite.style.display=active?'grid':'none';
function place(x,y){
  if(option==='A')window.spike.send({kind:'move',x,y});
  else sprite.style.transform=`translate(${x}px,${y}px)`;
}
sprite.addEventListener('mouseenter',()=>{hoverCount++;window.spike.send({kind:'hover'});if(interactive)window.spike.send({kind:'capture',capture:true});});
sprite.addEventListener('mouseleave',()=>{if(interactive&&!dragging)window.spike.send({kind:'capture',capture:false});});
sprite.addEventListener('pointerdown',event=>{if(!interactive)return;dragging=true;const rect=sprite.getBoundingClientRect();offset=option==='A'?{x:event.clientX,y:event.clientY}:{x:event.clientX-rect.left,y:event.clientY-rect.top};sprite.setPointerCapture(event.pointerId);});
sprite.addEventListener('pointermove',event=>{if(!dragging||!config)return;
  if(option==='A')window.spike.send({kind:'move',x:event.screenX-offset.x-Number(query.get('originX')),y:event.screenY-offset.y-Number(query.get('originY'))});
  else sprite.style.transform=`translate(${event.clientX-offset.x}px,${event.clientY-offset.y}px)`;
});
sprite.addEventListener('pointerup',event=>{if(!dragging)return;dragging=false;sprite.releasePointerCapture(event.pointerId);window.spike.send({kind:'capture',capture:false});});
window.spike.onPhase(message=>{
  config=message.config;sprite.style.width=`${config.spriteDip}px`;sprite.style.height=`${config.spriteDip}px`;
  if(heartbeatTimer===null)heartbeatTimer=setInterval(()=>window.spike.send({kind:'heartbeat'}),message.heartbeatMs);
  const base={x:Math.max(config.paddingDip,width-config.smallWindowWidthDip-config.travelDip),y:Math.max(config.paddingDip,height-config.smallWindowHeightDip)};
  if(option==='A')sprite.style.transform=`translate(${config.paddingDip}px,${config.paddingDip}px)`;
  else place(base.x,base.y);
  const samples=[];let previous=null,start=null;
  const frame=now=>{
    if(start===null)start=now;
    if(previous!==null&&samples.length<config.frameSampleCap)samples.push(now-previous);
    previous=now;
    const progress=Math.min(1,(now-start)/message.durationMs);
    if(message.name==='scripted-drag'&&active) {
      const x=base.x+config.travelDip*(.5-.5*Math.cos(progress*Math.PI*2*config.pathCycles));
      const y=base.y;
      place(x,y);
    }
    if(progress<1)requestAnimationFrame(frame);
    else window.spike.send({kind:'stats',phase:message.name,frameMs:samples,hoverCount});
  };
  requestAnimationFrame(frame);
});

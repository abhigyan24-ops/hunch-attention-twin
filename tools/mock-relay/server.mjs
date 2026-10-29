import http from 'node:http';
import {WebSocketServer,WebSocket} from 'ws';
import {validateFrame} from '../lib/contract-validate.mjs';
const pairing=new Set(['pair_offer','pair_join','pair_reveal','pair_confirm','pair_cancel']);
const session=new Set(['hello','ping','pong','events','ack','state','pet_event','held_list','command','cmd_ack','error']);
export async function startMockRelay(options={}){
 const now=options.nowFn??Date.now,ttl=options.rendezvousTtlMs??300000,idle=options.idleTtlMs??86400000;
 const joinAttempts=new Map();
 const rooms=new Map(),stats={connections:0,disconnections:0,framesForwarded:0,framesRejected:0,roomsCreated:0,roleCollisions:0,thirdClientRejections:0,joinRateLimited:0};
 const server=http.createServer((req,res)=>{res.writeHead(404);res.end();});
 const wss=new WebSocketServer({noServer:true,maxPayload:65536});
 function registerJoin(ip){for(const [key,list] of joinAttempts){const fresh=list.filter(ts=>now()-ts<60000);if(fresh.length)joinAttempts.set(key,fresh);else joinAttempts.delete(key);}const list=joinAttempts.get(ip)??[];if(list.length>=10||!joinAttempts.has(ip)&&joinAttempts.size>=4096){stats.joinRateLimited++;return false;}list.push(now());joinAttempts.set(ip,list);return true;}
 function expireRooms(){for(const [id,room] of rooms){if(room.type==='rendezvous'?now()-room.createdAt>=ttl:now()-room.lastActivity>=idle){room.offer=null;for(const client of room.clients.values())client.close(1008,'room_expired');rooms.delete(id);}}}
 const timer=setInterval(expireRooms,1000);timer.unref();
 server.on('upgrade',(req,socket,head)=>{
  expireRooms();let parsed;try{parsed=new URL(req.url,'http://127.0.0.1');}catch{socket.destroy();return;}
  const id=parsed.searchParams.get('room'),device=parsed.searchParams.get('device');
  if(parsed.pathname!=='/ws'||!id||!/^[0-9a-f]{64}$/i.test(id)||!['phone','laptop'].includes(device)){socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');return;}
  const ip=req.socket.remoteAddress??'loopback';let joinCounted=false;
  if(rooms.get(id)?.type==='rendezvous'){if(!registerJoin(ip)){socket.end('HTTP/1.1 429 Too Many Requests\r\n\r\n');return;}joinCounted=true;}
  wss.handleUpgrade(req,socket,head,ws=>{
   let room=rooms.get(id);if(!room){room={type:null,clients:new Map(),createdAt:now(),lastActivity:now(),offer:null,joined:false,revealed:false};rooms.set(id,room);stats.roomsCreated++;}
   if(room.clients.has(device)){stats.roleCollisions++;ws.close(1008,'role_collision');return;}
   if(room.clients.size>=2){stats.thirdClientRejections++;ws.close(1008,'room_full');return;}
   stats.connections++;room.clients.set(device,ws);room.lastActivity=now();const times=[];
   const reject=code=>{stats.framesRejected++;ws.close(1008,code);};
   ws.on('error',()=>{});
   ws.on('message',(data,binary)=>{
    if(rooms.get(id)!==room){reject('room_expired');return;}room.lastActivity=now();
    while(times.length&&now()-times[0]>=1000)times.shift();times.push(now());if(times.length>50){reject('rate_limited');return;}
    let frame;try{if(binary)throw Error();frame=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(data));}catch{reject('schema_invalid');return;}
    if(!validateFrame(frame).valid){reject('schema_invalid');return;}
    if(frame.device&&frame.device!==device){reject('wrong_role');return;}
    if(room.type===null){if(frame.t==='pair_offer'&&device==='laptop')room.type='rendezvous';else if(frame.t==='hello')room.type='session';else{reject('initial_frame');return;}}
    if(!(room.type==='rendezvous'?pairing:session).has(frame.t)){reject('room_type');return;}
    if(room.type==='rendezvous'){
     if(frame.t==='pair_offer'){if(!joinCounted){if(!registerJoin(ip)){reject('join_rate_limited');return;}joinCounted=true;}if(device!=='laptop'||room.offer!==null){reject('offer_immutable');return;}room.offer=JSON.stringify(frame);}
     if(frame.t==='pair_join'){if(device!=='phone'||!room.offer||room.joined){reject('join_order');return;}room.joined=true;}
     if(frame.t==='pair_reveal'){if(device!=='laptop'||!room.joined||room.revealed){reject('reveal_order');return;}room.revealed=true;}
     if(frame.t==='pair_confirm'&&!room.revealed){reject('confirm_order');return;}
    }
    const peer=room.clients.get(device==='phone'?'laptop':'phone');if(peer?.readyState===WebSocket.OPEN){peer.send(JSON.stringify(frame));stats.framesForwarded++;}
    if(frame.t==='pair_cancel'){room.offer=null;rooms.delete(id);for(const client of room.clients.values())client.close(1008,'pair_cancelled');}
   });
   ws.on('close',()=>{stats.disconnections++;if(room.clients.get(device)===ws)room.clients.delete(device);});
   if(device==='phone'&&room.offer&&room.type==='rendezvous'){ws.send(room.offer);stats.framesForwarded++;}
  });
 });
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(options.port??0,options.host??'127.0.0.1',resolve);});
 return {server,wss,rooms,stats,port:server.address().port,expireRooms,close:()=>new Promise(resolve=>{clearInterval(timer);for(const ws of wss.clients)ws.terminate();wss.close(()=>server.close(resolve));})};
}
if(process.argv[1]?.replaceAll('\\','/').endsWith('/tools/mock-relay/server.mjs')){const index=process.argv.indexOf('--port');const relay=await startMockRelay({port:index<0?8080:Number(process.argv[index+1])});console.log('Mock relay listening on port '+relay.port);}

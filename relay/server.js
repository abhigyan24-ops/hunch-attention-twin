import http from 'node:http';
import fs from 'node:fs';
import {isIP} from 'node:net';
import {pathToFileURL} from 'node:url';
import {WebSocket,WebSocketServer} from 'ws';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
const pairing=new Set(['pair_offer','pair_join','pair_reveal','pair_confirm','pair_cancel']);
const session=new Set(['hello','ping','pong','events','ack','state','pet_event','held_list','command','cmd_ack','error']);
const ajv=new Ajv2020({strict:true});addFormats(ajv);
const validators=new Map([...pairing,'hello','encrypted_frame'].map(name=>[name,ajv.compile(JSON.parse(fs.readFileSync(new URL(`../contracts/sync/schemas/${name}.schema.json`,import.meta.url),'utf8')))]));
function valid(frame){if(!frame||typeof frame!=='object'||Array.isArray(frame))return false;const validator=validators.get(pairing.has(frame.t)||frame.t==='hello'?frame.t:'encrypted_frame');const result=Boolean(validator?.(frame));if(validator)validator.errors=null;return result;}
function limit(value,fallback){const n=Number(value??fallback);if(!Number.isSafeInteger(n)||n<=0)throw Error('invalid_relay_limit');return n;}
export function clientIp(req){const remote=req.socket.remoteAddress??'unknown';if(remote==='127.0.0.1'||remote==='::ffff:127.0.0.1'){const forwarded=req.headers['x-forwarded-for'];if(typeof forwarded==='string'){const first=forwarded.split(',')[0].trim();if(isIP(first))return first;}}return remote;}
export function createRelayServer(options={}){
 const now=options.nowFn??Date.now,max=limit(options.maxFrameBytes??options.MAX_FRAME_BYTES??process.env.MAX_FRAME_BYTES,65536),rate=limit(options.rateLimitPerSec??options.RATE_LIMIT_PER_SEC??process.env.RATE_LIMIT_PER_SEC,50),ttl=limit(options.rendezvousTtlMs,300000),idle=limit(options.idleTtlMs??options.IDLE_TTL_MS??process.env.IDLE_TTL_MS,86400000);
 const logger=options.logger??(entry=>console.log(JSON.stringify(entry))),rooms=new Map(),attempts=new Map();
 const stats={forwarded:0,rejected:0,roleCollisions:0,joinRateLimited:0};
 const server=http.createServer((req,res)=>{if(req.method==='GET'&&req.url==='/healthz'){res.writeHead(200,{'Content-Type':'text/plain'});res.end('ok');}else{res.writeHead(404);res.end();}});
 const wss=new WebSocketServer({noServer:true,maxPayload:max,perMessageDeflate:false});
 function log(id,device,t,byteLength,reason){try{logger({room:id.slice(0,8),device,type:t,byteLength,...(reason?{reason}:{})});}catch{}}
 function register(ip){for(const [key,list]of attempts){const fresh=list.filter(ts=>now()-ts<60000);if(fresh.length)attempts.set(key,fresh);else attempts.delete(key);}const list=attempts.get(ip)??[];if(list.length>=10){stats.joinRateLimited++;return false;}list.push(now());attempts.set(ip,list);return true;}
 function destroy(id,room,reason){if(rooms.get(id)!==room)return;rooms.delete(id);room.offer=null;for(const ws of room.clients.values())ws.close(1008,reason);}
 function expireIdleRooms(){for(const [id,room]of rooms)if(room.type==='rendezvous'?now()-room.createdAt>=ttl:now()-room.lastActivity>=idle)destroy(id,room,'room_expired');}
 server.on('upgrade',(req,socket,head)=>{
  expireIdleRooms();let url;try{url=new URL(req.url,'http://relay.local');}catch{socket.destroy();return;}
  const raw=url.searchParams.get('room'),device=url.searchParams.get('device');if(url.pathname!=='/ws'||!/^[a-f0-9]{64}$/i.test(raw??'')||!['phone','laptop'].includes(device)||url.searchParams.getAll('room').length!==1||url.searchParams.getAll('device').length!==1){socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');return;}
  const id=raw.toLowerCase(),ip=clientIp(req);let counted=false;if(rooms.get(id)?.type==='rendezvous'){if(!register(ip)){socket.end('HTTP/1.1 429 Too Many Requests\r\n\r\n');return;}counted=true;}
  wss.handleUpgrade(req,socket,head,ws=>{
   ws.on('error',()=>{});let room=rooms.get(id);if(!room){room={type:null,createdAt:now(),lastActivity:now(),clients:new Map(),offer:null,joined:false,revealed:false};rooms.set(id,room);}
   const reject=reason=>{stats.rejected++;log(id,device,'rejected',0,reason);ws.close(1008,reason);};
   if(room.clients.has(device)||room.clients.size>=2){stats.roleCollisions++;reject('role_collision');if(room.type==='rendezvous'){for(const peer of room.clients.values())if(peer.readyState===WebSocket.OPEN)peer.send(JSON.stringify({v:1,t:'pair_cancel',reason:'third_client'}));destroy(id,room,'third_client');}return;}
   room.clients.set(device,ws);const arrivals=[];
   ws.on('close',()=>{if(room.clients.get(device)===ws)room.clients.delete(device);});
   ws.on('message',(data,binary)=>{
    if(ws.readyState!==WebSocket.OPEN||rooms.get(id)!==room||room.clients.get(device)!==ws)return;
    expireIdleRooms();if(rooms.get(id)!==room)return;
    while(arrivals.length&&now()-arrivals[0]>=1000)arrivals.shift();arrivals.push(now());if(arrivals.length>rate){reject('rate_limited');return;}
    const bytes=Buffer.byteLength(data);if(bytes>max){ws.close(1009,'too_large');return;}
    let frame;try{if(binary)throw Error();frame=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(data));}catch{reject('schema_invalid');return;}
    if(!valid(frame)||frame.v!==1){reject('schema_invalid');return;}
    if(frame.device&&frame.device!==device){reject('wrong_role');return;}
    if(room.type===null){if(frame.t==='pair_offer'&&device==='laptop'){room.type='rendezvous';room.createdAt=now();}else if(session.has(frame.t))room.type='session';else{reject('initial_frame');return;}}
    if(!(room.type==='rendezvous'?pairing:session).has(frame.t)){reject('room_type');return;}
    if(room.type==='rendezvous'){
     if(!counted){if(!register(ip)){reject('join_rate_limited');return;}counted=true;}
     if(frame.t==='pair_offer'){if(device!=='laptop'||room.offer){reject('offer_immutable');return;}room.offer=JSON.stringify(frame);}
     if(frame.t==='pair_join'){if(device!=='phone'||!room.offer||room.joined){reject('join_order');return;}room.joined=true;}
     if(frame.t==='pair_reveal'){if(device!=='laptop'||!room.joined||room.revealed){reject('reveal_order');return;}room.revealed=true;}
     if(frame.t==='pair_confirm'&&!room.revealed){reject('confirm_order');return;}
    }
    room.lastActivity=now();log(id,device,frame.t,bytes);const peer=room.clients.get(device==='phone'?'laptop':'phone');
    if(peer?.readyState===WebSocket.OPEN){if(peer.bufferedAmount>max*rate){peer.close(1008,'slow_peer');return;}peer.send(data,{binary:false},()=>{});stats.forwarded++;}
    if(frame.t==='pair_cancel')destroy(id,room,'pair_cancelled');
   });
   if(device==='phone'&&room.type==='rendezvous'&&room.offer){ws.send(room.offer);stats.forwarded++;}
  });
 });
 const timer=setInterval(expireIdleRooms,Math.min(1000,ttl,idle));timer.unref();
 const ready=new Promise((resolve,reject)=>{server.once('error',reject);server.listen(options.port??Number(process.env.PORT??8080),options.host??'127.0.0.1',()=>{server.off('error',reject);resolve(server.address());});});
 let closing;function close(){return closing??=new Promise(resolve=>{clearInterval(timer);for(const ws of wss.clients)ws.terminate();rooms.clear();wss.close(()=>server.close(resolve));});}
 return {server,wss,rooms,stats,ready,close,expireIdleRooms,address:()=>server.address()};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){const relay=createRelayServer({host:process.env.HOST??'0.0.0.0'});relay.ready.catch(()=>{console.error('Relay startup failed');process.exitCode=1;});for(const signal of ['SIGINT','SIGTERM'])process.once(signal,()=>void relay.close());}

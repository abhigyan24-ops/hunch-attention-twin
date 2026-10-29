import http from 'node:http';import {WebSocket,WebSocketServer} from 'ws';import {createHash,timingSafeEqual} from 'node:crypto';
import {validateIngest} from './validate';import type {ClientKind,FeedMessage} from './types';
export interface IngestOptions {port:number;token:string;allowedExtensionIds:string[];now?:()=>number;authTimeoutMs?:number;onMessage:(client:ClientKind,message:FeedMessage)=>void;onConnection?:(client:ClientKind,connected:boolean)=>void}
export class IngestServer {
 private http=http.createServer((req,res)=>{res.writeHead(404);res.end();});private wss=new WebSocketServer({noServer:true,maxPayload:4096});
 private ids:Set<string>;private clients=new Map<ClientKind,WebSocket>();private origins=new Map<WebSocket,string|undefined>();
 private failures:number[]=[];private blockedUntil=0;private counters:Record<string,number>={};private closed=false;
 private constructor(private options:IngestOptions){this.ids=new Set(options.allowedExtensionIds);}
 static async start(options:IngestOptions){const server=new IngestServer(options);await server.listen();return server;}
 get port(){const address=this.http.address();return typeof address==='object'&&address?address.port:0;}
 get diagnostics(){return {...this.counters};}
 private now(){return (this.options.now??Date.now)();}
 private count(reason:string){this.counters[reason]=(this.counters[reason]??0)+1;}
 private failAuth(){const now=this.now();this.failures=this.failures.filter(ts=>now-ts<60000);this.failures.push(now);this.count('auth_failed');this.count('ingest.invalid');if(this.failures.length>=5){this.blockedUntil=now+300000;this.failures=[];}}
 setAllowedExtensionIds(ids:string[]){if(!ids.every(id=>/^[a-p]{32}$/.test(id)))throw Error('invalid_extension_id');this.ids=new Set(ids);for(const [ws,origin] of this.origins)if(origin&&!this.allowed(origin))ws.close(1008,'allowlist_changed');}
 private allowed(origin:string){return [...this.ids].some(id=>origin==='chrome-extension://'+id);}
 private async listen(){
  this.http.on('upgrade',(req,socket,head)=>{const origin=req.headers.origin;if(origin!==undefined&&!this.allowed(origin)){this.count('origin_rejected');socket.end('HTTP/1.1 403 Forbidden\r\n\r\n');return;}if(req.url!=='/ingest'){socket.end('HTTP/1.1 404 Not Found\r\n\r\n');return;}if(this.now()<this.blockedUntil){this.count('blocked');socket.end('HTTP/1.1 403 Forbidden\r\n\r\n');return;}
   this.wss.handleUpgrade(req,socket,head,ws=>this.connect(ws,origin));});
  await new Promise<void>((resolve,reject)=>{this.http.once('error',()=>reject(Error('ingest_bind_failed')));this.http.listen(this.options.port,'127.0.0.1',resolve);});
 }
 private connect(ws:WebSocket,origin:string|undefined){this.origins.set(ws,origin);let client:ClientKind|null=null;const times:number[]=[];let rateNotice=-Infinity;
  const timer=setTimeout(()=>{if(!client){this.failAuth();ws.close(1008,'auth_timeout');}},this.options.authTimeoutMs??3000);timer.unref();
  const error=(code:'schema_invalid'|'rate_limited',reason:string)=>{this.count('ingest.invalid');this.count(reason);if(ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify({kind:'error',code}));};
  ws.on('error',(error:Error&{code?:string})=>{this.count('ingest.invalid');this.count(error.code==='WS_ERR_UNSUPPORTED_MESSAGE_LENGTH'?'too_large':'frame_error');});
  ws.on('message',(data,binary)=>{
   const now=this.now();while(times.length&&now-times[0]>=1000)times.shift();if(times.length>=20){this.count('rate_limited');this.count('ingest.invalid');if(now-rateNotice>=1000){rateNotice=now;if(ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify({kind:'error',code:'rate_limited'}));}return;}times.push(now);
   let value:unknown;try{if(binary)throw Error();value=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(data as Buffer));}catch{if(!client){this.failAuth();ws.close(1008,'auth_invalid');}else error('schema_invalid','json_invalid');return;}
   if(!validateIngest(value)){if(!client){this.failAuth();ws.close(1008,'auth_invalid');}else error('schema_invalid','schema_invalid');return;}
   if(!client){if(this.now()<this.blockedUntil){ws.close(1008,'auth_blocked');return;}
    if(value.kind!=='auth'||!timingSafeEqual(createHash('sha256').update(value.kind==='auth'?value.token:'').digest(),createHash('sha256').update(this.options.token).digest())){this.failAuth();ws.close(1008,'auth_invalid');return;}
    clearTimeout(timer);client=value.client;const old=this.clients.get(client);this.clients.set(client,ws);old?.close(4002,'replaced');this.count('auth_success');try{this.options.onConnection?.(client,true);}catch{this.count('handler_failed');}ws.send(JSON.stringify({kind:'auth_ok',serverVersion:'1',config:{heartbeatSec:5}}));return;
   }
   if(this.clients.get(client)!==ws)return;
   const allowed=client==='vscode'?['ext_heartbeat','edit_burst','resume_first_edit','vscode_state']:['ext_heartbeat','tab_category'];
   if(value.kind==='auth'||!allowed.includes(value.kind)){error('schema_invalid','client_kind');return;}
   try{this.options.onMessage(client,value);}catch{error('schema_invalid','handler_failed');}
  });
  ws.on('close',()=>{clearTimeout(timer);this.origins.delete(ws);if(client&&this.clients.get(client)===ws){this.clients.delete(client);try{this.options.onConnection?.(client,false);}catch{this.count('handler_failed');}}});
 }
 async close(){if(this.closed)return;this.closed=true;for(const ws of this.wss.clients)ws.terminate();await new Promise<void>(resolve=>this.wss.close(()=>this.http.close(()=>resolve())));}
}

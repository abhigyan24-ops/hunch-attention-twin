import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { WebSocket } from 'ws';
import { createRelayServer } from '../server.js';

const room = 'a'.repeat(64);
const ping = {type:'ping',t0:0,t1:0,t2:0,t3:0};
async function setup(t, options = {}) {
  const relay=createRelayServer({port:0,logger:()=>{},...options});const address=await relay.ready;
  t.after(()=>relay.close());
  const base=`ws://127.0.0.1:${address.port}`;
  async function connect(device='phone',id=room,path='/ws') {
    const ws=new WebSocket(`${base}${path}?room=${id}&device=${device}`);ws.on('error',()=>{});
    await once(ws,'open');return ws;
  }
  return {relay,base,connect};
}
const message = ws => once(ws,'message').then(([data])=>JSON.parse(data.toString()));
const closed = ws => once(ws,'close').then(([code])=>code);
test('GET /healthz returns 200 ok',async t=>{
  const {base}=await setup(t);const response=await fetch(base.replace('ws:','http:')+'/healthz');assert.equal(response.status,200);assert.equal(await response.text(),'ok');
});
test('invalid room, device and path close 4001',async t=>{
  const {connect}=await setup(t);
  for(const [device,id,path] of [['phone','bad','/ws'],['watch',room,'/ws'],['phone',room,'/wrong']])assert.equal(await closed(await connect(device,id,path)),4001);
});
test('pair forwards bidirectionally without echo or cross-room leakage',async t=>{
  const {connect}=await setup(t);const phone=await connect(),laptop=await connect('laptop'),other=await connect('phone','b'.repeat(64));
  let echoed=0,leaked=0;phone.on('message',()=>echoed++);other.on('message',()=>leaked++);
  let received=message(laptop);phone.send(JSON.stringify(ping));assert.deepEqual(await received,ping);
  assert.equal(echoed,0);received=message(phone);laptop.send(JSON.stringify({...ping,type:'pong'}));assert.equal((await received).type,'pong');
  await new Promise(resolve=>setTimeout(resolve,30));assert.equal(echoed,1);assert.equal(leaked,0);
});
test('same-role replacement closes old socket 4002; old close cannot remove new role',async t=>{
  const {connect}=await setup(t);const old=await connect(),laptop=await connect('laptop');const oldClosed=closed(old);const replacement=await connect();assert.equal(await oldClosed,4002);
  const received=message(replacement);laptop.send(JSON.stringify(ping));assert.deepEqual(await received,ping);
});
test('invalid JSON, array, null, binary, malformed UTF-8 close 4003',async t=>{
  const {connect}=await setup(t);
  for(const [value,binary]of [['{',false],['[]',false],['null',false],[JSON.stringify(ping),true],[Buffer.from([0xc3,0x28]),false]]){
    const ws=await connect();const end=closed(ws);ws.send(value,{binary});assert.equal(await end,4003);
  }
});
test('unknown type, extra content, bad schema and reserved ciphertext close 4004',async t=>{
  const {connect}=await setup(t);
  for(const frame of [{type:'unknown'}, {...ping,text:'SENSITIVE_SENTINEL'}, {type:'ack',upToSeq:-1},{type:'encrypted',ciphertext:'unapproved'}]){
    const ws=await connect();const end=closed(ws);ws.send(JSON.stringify(frame));assert.equal(await end,4004);
  }
});
test('oversized frame closes 1009',async t=>{
  const {connect}=await setup(t,{MAX_FRAME_BYTES:128});const ws=await connect();const end=closed(ws);ws.send('x'.repeat(129));assert.equal(await end,1009);
});
test('deep invalid JSON structure is rejected without overflowing privacy traversal',async t=>{
  const {connect,base}=await setup(t);const ws=await connect();const end=closed(ws);
  ws.send('{"type":"ping","nested":'+'['.repeat(5000)+'0'+']'.repeat(5000)+'}');
  assert.equal(await end,4004);
  assert.equal((await fetch(base.replace('ws:','http:')+'/healthz')).status,200);
});
test('sliding one-second rate limit closes 4008 and resets after window',async t=>{
  let now=1000;const {connect}=await setup(t,{nowFn:()=>now,RATE_LIMIT_PER_SEC:2});const ws=await connect();
  ws.send(JSON.stringify(ping));ws.send(JSON.stringify(ping));await new Promise(r=>setTimeout(r,20));now=2000;
  ws.send(JSON.stringify(ping));await new Promise(r=>setTimeout(r,20));assert.equal(ws.readyState,WebSocket.OPEN);
  const end=closed(ws);ws.send(JSON.stringify(ping));ws.send(JSON.stringify(ping));assert.equal(await end,4008);
});
test('fake clock expires idle rooms and active valid frames refresh TTL',async t=>{
  let now=0;const {relay,connect}=await setup(t,{nowFn:()=>now,IDLE_TTL_MS:100});const p=await connect(),l=await connect('laptop');
  now=90;const forwarded=message(l);p.send(JSON.stringify(ping));await forwarded;
  now=100;relay.expireIdleRooms();assert.equal(p.readyState,WebSocket.OPEN);
  const ends=[closed(p),closed(l)];now=190;relay.expireIdleRooms();assert.deepEqual(await Promise.all(ends),[4000,4000]);
  const fresh=await connect();assert.equal(fresh.readyState,WebSocket.OPEN);
});
test('logs contain only short room, role, allowed type and byteLength; rejected input never leaks',async t=>{
  const logs=[];const {connect}=await setup(t,{logger:entry=>logs.push(entry)});const p=await connect(),l=await connect('laptop');
  const received=message(l);p.send(JSON.stringify(ping));await received;
  const end=closed(p);p.send(JSON.stringify({...ping,nested:{text:'SENSITIVE_SENTINEL'}}));assert.equal(await end,4004);
  assert.equal(logs.length,1);assert.deepEqual(Object.keys(logs[0]).sort(),['byteLength','device','room','type']);assert.equal(logs[0].room,room.slice(0,8));
  assert.ok(!JSON.stringify(logs).includes('SENSITIVE_SENTINEL'));assert.ok(!JSON.stringify(logs).includes(room));
});
test('commands respect expiry and optional peer authorization hook',async t=>{
  const {connect}=await setup(t,{nowFn:()=>1000,authorizeCommand:()=>false});
  for(const expiresAt of [999,2000]){const ws=await connect();const end=closed(ws);ws.send(JSON.stringify({type:'command',commandId:'00000000-0000-4000-8000-000000000001',issuedAt:0,expiresAt,cmd:'kill_switch',args:{}}));assert.equal(await end,4004);}
});

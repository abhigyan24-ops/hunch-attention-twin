import { randomBytes } from 'node:crypto';
import { WebSocket } from 'ws';

const args = process.argv.slice(2);
const base = args[0];
const pair = args.includes('--pair');
const option = name => args[args.indexOf(name) + 1];
const room = pair ? randomBytes(32).toString('hex') : option('--room');
const devices = pair ? ['phone','laptop'] : [option('--device')];
const sockets = [];
let timer;
try {
  if (!base || !/^wss?:$/.test(new URL(base).protocol) || !/^[a-fA-F0-9]{64}$/.test(room ?? '') || devices.some(d => !['phone','laptop'].includes(d))) throw new Error();
  await Promise.race([
    new Promise((_, reject) => { timer = setTimeout(() => reject(new Error()), 7000); }),
    (async () => {
      const received = new Map();
      await Promise.all(devices.map(device => new Promise((resolve, reject) => {
        const url = new URL(base); url.pathname = '/ws'; url.searchParams.set('room',room); url.searchParams.set('device',device);
        const ws = new WebSocket(url); sockets.push(ws); received.set(device, new Set());
        ws.on('message', bytes => {
          try { const frame=JSON.parse(bytes.toString()); received.get(device).add(frame.type); }
          catch { reject(new Error()); }
        });
        ws.once('open',resolve); ws.on('error',reject); ws.on('close',() => reject(new Error()));
      })));
      for (let i=0;i<devices.length;i++) {
        const device=devices[i], ws=sockets.find(s => new URL(s.url).searchParams.get('device') === device);
        for (const frame of [{type:'hello',device,version:'1.0.0',lastSeqAcked:0},{type:'ping',t0:0,t1:0,t2:0,t3:0},{type:'pong',t0:0,t1:0,t2:0,t3:0},{type:'state',focusState:'working',[device==='phone'?'phoneState':'laptopState']:device==='phone'?'screen_off':'flowing',confidence:0.5,declared:null,heldCount:0}]) ws.send(JSON.stringify(frame));
      }
      await new Promise((resolve, reject) => {
        const poll=setInterval(() => {
          if (sockets.some(s=>s.readyState!==WebSocket.OPEN)) { clearInterval(poll);reject(new Error()); }
          else if ([...received.values()].every(types => ['hello','ping','pong','state'].every(type => types.has(type)))) { clearInterval(poll); resolve(); }
        },10); poll.unref();
      });
    })()
  ]);
  console.log(pair ? 'PAIR PASS: hello, ping, pong, state forwarded in both directions.' : 'PEER PASS: hello, ping, pong, state received from paired peer.');
} catch { console.error('Relay client failed or timed out.'); process.exitCode=1; }
finally { clearTimeout(timer);for(const ws of sockets)ws.terminate(); }

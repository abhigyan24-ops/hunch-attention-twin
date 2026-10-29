import {spawn} from 'node:child_process';import {once} from 'node:events';import {fileURLToPath} from 'node:url';import {createRelayServer} from '../../relay/server.js';
const relay=createRelayServer({port:0,logger:()=>{}});await relay.ready;
try{const child=spawn(process.execPath,[fileURLToPath(new URL('../../relay/test-client.mjs',import.meta.url)),`ws://127.0.0.1:${relay.address().port}/ws`,'--pair'],{stdio:'inherit'});const [code]=await once(child,'exit');if(code!==0)process.exitCode=code??1;}finally{await relay.close();}

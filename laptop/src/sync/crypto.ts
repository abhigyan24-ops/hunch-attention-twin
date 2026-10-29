import crypto from 'node:crypto';
export type Role='laptop'|'phone';export type Direction='l2p'|'p2l';
export const CODE_ALPHABET='23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
export function generatePairingCode(){return Array.from({length:6},()=>CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)]).join('');}
export function getRendezvousRoomId(code:string){if(!/^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/.test(code))throw Error('invalid_code');return crypto.createHash('sha256').update('dhyan-rendezvous-v1|'+code).digest('hex');}
export function generateP256KeyPair(scalar?:Buffer){const e=crypto.createECDH('prime256v1');if(scalar)e.setPrivateKey(scalar);else e.generateKeys();return {privateKey:e.getPrivateKey(),publicKey:e.getPublicKey()};}
export function computeECDH(key:Buffer,peer:Buffer){try{if(peer.length!==65||peer[0]!==4)throw Error();const e=crypto.createECDH('prime256v1');e.setPrivateKey(key);return e.computeSecret(peer);}catch{throw Error('invalid_public_key');}}
export function hkdfSha256(secret:Buffer,salt:Buffer|string,info:Buffer|string,length:number){return Buffer.from(crypto.hkdfSync('sha256',secret,Buffer.from(salt),Buffer.from(info),length));}
export function computePairCommit(pk:Buffer,n:Buffer){return crypto.createHash('sha256').update(Buffer.concat([pk,n])).digest();}
export function computeTranscriptHash(pkL:Buffer,pkP:Buffer,nL:Buffer,nP:Buffer){return crypto.createHash('sha256').update(Buffer.concat([Buffer.from('dhyan-pair-v1'),pkL,pkP,nL,nP])).digest();}
export function computeSas(t:Buffer){const s=String(t.readUInt32BE(0)%1000000).padStart(6,'0');return s.slice(0,3)+' '+s.slice(3);}
export function computePairConfirmMac(secret:Buffer,t:Buffer,role:Role){return crypto.createHmac('sha256',hkdfSha256(secret,t,'dhyan-pair-confirm-v1',32)).update('confirm|'+role).digest();}
export function derivePairingKeyAndRoom(secret:Buffer,t:Buffer){const PK=hkdfSha256(secret,t,'dhyan-pairing-key-v1',32);return {PK,sid:hkdfSha256(PK,'','dhyan-room-v1',32).toString('hex')};}
export function computeHelloMac(pk:Buffer,role:Role,sn:string,last:number){return crypto.createHmac('sha256',hkdfSha256(pk,'','dhyan-hello-mac-v1',32)).update(`hello|${role}|${sn}|${last}`).digest();}
export function deriveSessionKeys(pk:Buffer,l:Buffer,p:Buffer){const k=hkdfSha256(pk,Buffer.concat([l,p]),'dhyan-session-v1',64);return {k_l2p:Buffer.from(k.subarray(0,32)),k_p2l:Buffer.from(k.subarray(32))};}
export function buildNonce(ctr:number){if(!Number.isSafeInteger(ctr)||ctr<1||ctr>1e9)throw Error('counter_exhausted');const n=Buffer.alloc(12);n.writeBigUInt64BE(BigInt(ctr),4);return n;}
export function buildAad(t:string,d:Direction,ctr:number){return Buffer.from(`dhyan|1|${t}|${d}|${ctr}`,'ascii');}
export function encryptFramePayload(key:Buffer,t:string,d:Direction,ctr:number,plain:Buffer|string){const c=crypto.createCipheriv('aes-256-gcm',key,buildNonce(ctr));c.setAAD(buildAad(t,d,ctr));const bytes=Buffer.isBuffer(plain)?plain:Buffer.from(plain);return Buffer.concat([c.update(bytes),c.final(),c.getAuthTag()]).toString('base64');}
export function decodeBase64(value:string,length?:number){const b=Buffer.from(value,'base64');if(b.toString('base64')!==value||length!==undefined&&b.length!==length)throw Error('invalid_binary');return b;}
export function equalMac(expected:Buffer,value:string){try{const b=decodeBase64(value,expected.length);return crypto.timingSafeEqual(expected,b);}catch{return false;}}
export function decryptFramePayload(key:Buffer,t:string,d:Direction,ctr:number,value:string){try{const b=decodeBase64(value);if(b.length<16)throw Error();const c=crypto.createDecipheriv('aes-256-gcm',key,buildNonce(ctr));c.setAAD(buildAad(t,d,ctr));c.setAuthTag(b.subarray(-16));return Buffer.concat([c.update(b.subarray(0,-16)),c.final()]);}catch{throw Error('tag_failed');}}

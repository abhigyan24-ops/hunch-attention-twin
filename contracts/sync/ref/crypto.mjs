import {createECDH,createHash,createHmac,hkdfSync,createCipheriv,createDecipheriv,randomInt,timingSafeEqual} from 'node:crypto';

export const CODE_ALPHABET='23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const utf8=s=>Buffer.from(s,'utf8');
function sized(value,length){if(!Buffer.isBuffer(value)||value.length!==length)throw Error('invalid_binary_length');return value;}
function role(device){if(!['laptop','phone'].includes(device))throw Error('invalid_role');}
export function sha256(value){return createHash('sha256').update(value).digest();}
export function hmac(key,value){return createHmac('sha256',key).update(value).digest();}
export function hkdf(ikm,salt,info,length){
 if(!Buffer.isBuffer(ikm)||!Buffer.isBuffer(salt)||!Buffer.isBuffer(info)||!Number.isInteger(length)||length<1||length>255*32)throw Error('invalid_hkdf_input');
 return Buffer.from(hkdfSync('sha256',ikm,salt,info,length));
}
export function keyPair(privateScalar){
 sized(privateScalar,32);const ecdh=createECDH('prime256v1');
 try{ecdh.setPrivateKey(privateScalar);}catch{throw Error('invalid_private_scalar');}
 return {privateScalar:Buffer.from(privateScalar),publicPoint:ecdh.getPublicKey(undefined,'uncompressed')};
}
export function sharedSecret(privateScalar,peerPublicPoint){
 sized(privateScalar,32);sized(peerPublicPoint,65);if(peerPublicPoint[0]!==4)throw Error('invalid_public_point');
 try{const ecdh=createECDH('prime256v1');ecdh.setPrivateKey(privateScalar);return ecdh.computeSecret(peerPublicPoint);}catch{throw Error('invalid_ecdh_input');}
}
export function commitment(publicPoint,nonce){return sha256(Buffer.concat([sized(publicPoint,65),sized(nonce,16)]));}
export function transcript(pkL,pkP,nL,nP){return sha256(Buffer.concat([utf8('dhyan-pair-v1'),sized(pkL,65),sized(pkP,65),sized(nL,16),sized(nP,16)]));}
export function sas(T){return (sized(T,32).readUInt32BE(0)%1000000).toString().padStart(6,'0');}
export function confirmationKey(secret,T){return hkdf(sized(secret,32),sized(T,32),utf8('dhyan-pair-confirm-v1'),32);}
export function confirmationMac(kConf,device){role(device);return hmac(sized(kConf,32),utf8('confirm|'+device));}
export function pairingKey(secret,T){return hkdf(sized(secret,32),sized(T,32),utf8('dhyan-pairing-key-v1'),32);}
export function sessionRoomId(PK){return hkdf(sized(PK,32),Buffer.alloc(0),utf8('dhyan-room-v1'),32).toString('hex');}
export function helloKey(PK){return hkdf(sized(PK,32),Buffer.alloc(0),utf8('dhyan-hello-mac-v1'),32);}
export function helloMac(kHello,device,sn,lastSeqAcked){
 role(device);sized(sn,16);if(!Number.isSafeInteger(lastSeqAcked)||lastSeqAcked<0)throw Error('invalid_sequence');
 return hmac(sized(kHello,32),utf8('hello|'+device+'|'+sn.toString('base64')+'|'+lastSeqAcked));
}
export function sessionKeys(PK,snL,snP){
 const okm=hkdf(sized(PK,32),Buffer.concat([sized(snL,16),sized(snP,16)]),utf8('dhyan-session-v1'),64);
 return {okm,kL2P:Buffer.from(okm.subarray(0,32)),kP2L:Buffer.from(okm.subarray(32,64))};
}
export function counterNonce(ctr){
 if(!Number.isSafeInteger(ctr)||ctr<1||ctr>1000000000)throw Error('invalid_counter');
 const nonce=Buffer.alloc(12);nonce.writeBigUInt64BE(BigInt(ctr),4);return nonce;
}
export function aad(type,direction,ctr){
 if(!['ping','pong','events','ack','state','pet_event','held_list','command','cmd_ack','error'].includes(type))throw Error('invalid_type');
 if(!['l2p','p2l'].includes(direction))throw Error('invalid_direction');counterNonce(ctr);
 return utf8('dhyan|1|'+type+'|'+direction+'|'+ctr);
}
/** Byte primitive only: callers validate protocol body/envelope/size before applying anything. */
export function encryptBytes(key,nonce,aadBytes,plaintext){
 sized(key,32);sized(nonce,12);if(!Buffer.isBuffer(aadBytes)||!Buffer.isBuffer(plaintext))throw Error('invalid_binary_input');
 const cipher=createCipheriv('aes-256-gcm',key,nonce,{authTagLength:16});cipher.setAAD(aadBytes);
 return Buffer.concat([cipher.update(plaintext),cipher.final(),cipher.getAuthTag()]);
}
export function decryptBytes(key,nonce,aadBytes,ciphertextWithTag){
 sized(key,32);sized(nonce,12);if(!Buffer.isBuffer(aadBytes)||!Buffer.isBuffer(ciphertextWithTag)||ciphertextWithTag.length<16)throw Error('invalid_ciphertext');
 try{const decipher=createDecipheriv('aes-256-gcm',key,nonce,{authTagLength:16});decipher.setAAD(aadBytes);
 decipher.setAuthTag(ciphertextWithTag.subarray(-16));return Buffer.concat([decipher.update(ciphertextWithTag.subarray(0,-16)),decipher.final()]);}
 catch{throw Error('authentication_failed');}
}
export function equalMac(a,b){return Buffer.isBuffer(a)&&Buffer.isBuffer(b)&&a.length===b.length&&timingSafeEqual(a,b);}
export function rendezvousRoomId(code){
 if(typeof code!=='string'||code.length!==6||![...code].every(c=>CODE_ALPHABET.includes(c)))throw Error('invalid_code');
 return sha256(utf8('dhyan-rendezvous-v1|'+code)).toString('hex');
}
export function generateCode(){return Array.from({length:6},()=>CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]).join('');}

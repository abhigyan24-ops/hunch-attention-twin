import * as c from '../../contracts/sync/ref/crypto.mjs';

/** Public deterministic test inputs only. NEVER use this fixture for real pairing. */
export function generateVectors(){
 const skL=Buffer.from('01'.padStart(64,'0'),'hex'),skP=Buffer.from('02'.padStart(64,'0'),'hex');
 const nL=Buffer.from('000102030405060708090a0b0c0d0e0f','hex'),nP=Buffer.from('f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff','hex');
 const snL=Buffer.from('101112131415161718191a1b1c1d1e1f','hex'),snP=Buffer.from('202122232425262728292a2b2c2d2e2f','hex');
 const pkL=c.keyPair(skL).publicPoint,pkP=c.keyPair(skP).publicPoint;
 const secret=c.sharedSecret(skL,pkP),T=c.transcript(pkL,pkP,nL,nP),kConf=c.confirmationKey(secret,T),PK=c.pairingKey(secret,T);
 const kHello=c.helloKey(PK),keys=c.sessionKeys(PK,snL,snP);
 const messages=[{t:'ping',d:'l2p',ctr:1,key:keys.kL2P,plain:{mid:'00000000-0000-4000-8000-000000000001',ts:1767225600000,b:{t0:1767225600000}}},
 {t:'pong',d:'p2l',ctr:17,key:keys.kP2L,plain:{mid:'00000000-0000-4000-8000-000000000002',ts:1767225600010,b:{t0:1767225600000,t1:1767225600005,t2:1767225600010}}}];
 return {notice:'PUBLIC TEST VECTORS ONLY — fixed scalars/nonces are not secrets and MUST NOT be used in production',contractVersion:'1.0.0',
 inputs:{code:'ABC234',privateLHex:skL.toString('hex'),privatePHex:skP.toString('hex'),nLHex:nL.toString('hex'),nPHex:nP.toString('hex'),snLHex:snL.toString('hex'),snPHex:snP.toString('hex')},
 pairing:{pkLBase64:pkL.toString('base64'),pkPBase64:pkP.toString('base64'),secretHex:secret.toString('hex'),commitmentHex:c.commitment(pkL,nL).toString('hex'),
 transcriptHex:T.toString('hex'),sasDigits:c.sas(T),kConfHex:kConf.toString('hex'),confirmLBase64:c.confirmationMac(kConf,'laptop').toString('base64'),confirmPBase64:c.confirmationMac(kConf,'phone').toString('base64'),
 pairingKeyHex:PK.toString('hex'),sid:c.sessionRoomId(PK),rendezvous:c.rendezvousRoomId('ABC234')},
 session:{kHelloHex:kHello.toString('hex'),helloLBase64:c.helloMac(kHello,'laptop',snL,1234).toString('base64'),helloPBase64:c.helloMac(kHello,'phone',snP,5678).toString('base64'),
 okmHex:keys.okm.toString('hex'),kL2PHex:keys.kL2P.toString('hex'),kP2LHex:keys.kP2L.toString('hex')},
 encrypted:messages.map(m=>{const nonce=c.counterNonce(m.ctr),aad=c.aad(m.t,m.d,m.ctr),plaintext=Buffer.from(JSON.stringify(m.plain));
 return {t:m.t,d:m.d,ctr:m.ctr,nonceHex:nonce.toString('hex'),aadHex:aad.toString('hex'),plaintextUtf8:plaintext.toString('utf8'),plaintextHex:plaintext.toString('hex'),cBase64:c.encryptBytes(m.key,nonce,aad,plaintext).toString('base64')};})};
}

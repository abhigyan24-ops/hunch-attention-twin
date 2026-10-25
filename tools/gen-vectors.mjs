import fs from 'node:fs';
import crypto from 'node:crypto';
import {generateVectors} from './lib/contract-vectors.mjs';
const encoded=JSON.stringify(generateVectors(),null,2)+'\n';
fs.writeFileSync(new URL('../contracts/sync/test-vectors.json',import.meta.url),encoded);
console.log('Public fixed vectors generated; sha256='+crypto.createHash('sha256').update(encoded).digest('hex'));

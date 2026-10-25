import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
const defaultRoot=fileURLToPath(new URL('../',import.meta.url));
// Normalize line endings because this repository uses autocrlf on Windows.
export const hash=source=>crypto.createHash('sha256').update(source.replace(/\r\n/g,'\n')).digest('hex');
export function verifyHash(source,expected){if(hash(source)!==expected)throw Error('frozen_contract_changed');}
export function checkDecisionContract(root=defaultRoot){
 const lock=JSON.parse(fs.readFileSync(path.join(root,'tools/decision-contract.lock'),'utf8'));
 if(lock.version!==1||!Object.keys(lock.files).length)throw Error('invalid_lock_version');
 for(const [file,digest]of Object.entries(lock.files)){
 if(file.includes('..')||path.isAbsolute(file))throw Error('invalid_lock_path');
 verifyHash(fs.readFileSync(path.join(root,file),'utf8'),digest);
 }
 const dir=path.join(root,'phone/app/src/main/java/com/dhyan/app/twin/decision');
 const version=fs.readFileSync(path.join(dir,'DecisionContract.kt'),'utf8').match(/const val DECISION_CONTRACT_VERSION = (\d+)/);
 if(Number(version?.[1])!==lock.version)throw Error('version_mismatch');
 const defaults=fs.readFileSync(path.join(dir,'PolicyDefaults.kt'),'utf8');
 const rows=JSON.parse(fs.readFileSync(path.join(root,'core-spec/default-params.json'),'utf8')).appendixA;
 const symbols={KAPPA_D_PER_HOUR:'κ_d',LAMBDA_MISS_MIN:'λ_miss',C_DIGEST_MIN:'c_digest',C_REL:'c_rel',I_MAX:'I_max',I_SILENT:'I_silent',EPSILON:'ε',CONF_MIN:'conf_min',C_GLANCE_MIN:'c_glance',WINDOW_MIN:'W',DIGEST_INTERVAL_MIN:'τ_digest_max'};
 for(const [name,symbol]of Object.entries(symbols)){
 const row=rows.find(r=>r.symbol===symbol);const value=defaults.match(new RegExp(`const val ${name} = ([0-9.]+)`));
 if(!row||!value||Number(value[1])!==Number.parseFloat(row.default))throw Error('policy_default_mismatch:'+name);
 }
 return `decision-contract: PASS (version ${lock.version}; ${Object.keys(lock.files).length} frozen files; ${Object.keys(symbols).length} core defaults)`;
}
export function selfTest(){
 const source='const val version = 1\n';verifyHash(source,hash(source));
 let rejected=false;try{verifyHash(source+'// planted change\n',hash(source))}catch{rejected=true}
 if(!rejected)throw Error('contract_self_test_failed');
 return 'decision-contract self-test: PASS (planted mutation rejected)';
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){try{console.log(process.argv.includes('--self-test')?selfTest():checkDecisionContract(process.argv[2]));}catch(e){console.error('decision-contract: FAIL '+e.message);process.exitCode=1}}

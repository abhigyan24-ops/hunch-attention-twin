import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const defaultRoot=fileURLToPath(new URL('../',import.meta.url));
export function inspectSource(source) {
 if(/\bimport\s+(?:android\.|androidx\.|java\.io\.|java\.net\.)|\b(?:java\.io\.File|java\.net\.|RoomDatabase|Math\.random|Random\.Default|System\.currentTimeMillis|System\.nanoTime|UUID\.randomUUID)\b/.test(source)) throw Error('prohibited_dependency_or_nondeterminism');
}
export function checkDecisionPurity(root=defaultRoot){
 let count=0;
 for(const scope of ['twin/decision','twin/counterfactual','safety']) {
 const dir=path.join(root,'phone/app/src/main/java/com/dhyan/app',scope);
 if(!fs.existsSync(dir))throw Error('missing_owned_scope');
 for(const name of fs.readdirSync(dir,{recursive:true}).filter(n=>n.endsWith('.kt'))){inspectSource(fs.readFileSync(path.join(dir,name),'utf8'));count++;}
 }
 return `decision-purity: PASS (${count} Kotlin files)`;
}
export function selfTest(){
 for(const source of ['import android.app.Service','import androidx.room.Room','import java.io.File','java.net.Socket()','Math.random()','Random.Default.nextDouble()','System.currentTimeMillis()','System.nanoTime()','UUID.randomUUID()']){
 let rejected=false;try{inspectSource(source)}catch{rejected=true}if(!rejected)throw Error('purity_self_test_failed');
 }
 inspectSource('import java.time.ZoneId');return 'decision-purity self-test: PASS (9 planted violations)';
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){try{console.log(process.argv.includes('--self-test')?selfTest():checkDecisionPurity(process.argv[2]));}catch(e){console.error('decision-purity: FAIL '+e.message);process.exitCode=1}}

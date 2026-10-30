const fs=require('node:fs');
const {analyze}=require('./analyze.cjs');
const result=analyze(JSON.parse(fs.readFileSync(process.argv[2],'utf8')));
console.log(JSON.stringify(result));
if(!result.complete)process.exitCode=1;

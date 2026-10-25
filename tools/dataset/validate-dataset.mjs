import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';

export const labels=['urgent','important','normal','engagement_bait','promo'];
export const senderTypes=['family','colleague','friend','app','unknown','bank_or_otp','delivery'];
export const languages=['EN','Hinglish'];
const categories=JSON.parse(fs.readFileSync(new URL('../../core-spec/categories.json',import.meta.url),'utf8')).categories;
export const rowSchema={type:'object',properties:{
  id:{type:'string',minLength:1},lang:{type:'string',enum:languages},appCat:{type:'string',enum:categories},
  pkg:{type:'string',minLength:1},senderType:{type:'string',enum:senderTypes},label:{type:'string',enum:labels},
  hasReplyAction:{type:'boolean'},title:{type:'string',minLength:1,maxLength:200},body:{type:'string',minLength:1,maxLength:1000},synthetic:{type:'boolean',const:true}
},required:['id','lang','appCat','pkg','senderType','label','hasReplyAction','title','body','synthetic'],additionalProperties:false};
const check=new Ajv2020({strict:true}).compile(rowSchema);
export function validateRow(row){return check(row) ? {valid:true,errors:[]} : {valid:false,errors:['dataset_row_schema_invalid']};}
const normalize=value=>value.normalize('NFKC').toLowerCase().replace(/\s+/gu,' ').trim();
export function contentKey(row){return JSON.stringify([row.lang,normalize(row.title),normalize(row.body)]);}
export function deduplicateRows(rows){
  const ids=new Map(),contents=new Map(),unique=[];
  for(const row of rows){
    if(!validateRow(row).valid)throw new Error('dataset_row_schema_invalid');
    const key=contentKey(row),idMatch=ids.get(row.id),contentMatch=contents.get(key);
    if((idMatch && contentKey(idMatch)!==key) || (contentMatch && (contentMatch.label!==row.label || contentMatch.senderType!==row.senderType)))throw new Error('conflicting_duplicate');
    if(idMatch||contentMatch)continue;
    ids.set(row.id,row);contents.set(key,row);unique.push(row);
  }
  return unique;
}
export function validateDataset(rows,{requireCoverage=true}={}){
  const errors=[],counts=Object.fromEntries(labels.map(label=>[label,0]));
  if(!Array.isArray(rows)||!rows.length)return {valid:false,errors:['dataset_empty'],rowCount:0,labelCounts:counts};
  const ids=new Set(),contents=new Set(),langs=new Set(),senders=new Set();
  for(const row of rows){
    if(!validateRow(row).valid){errors.push('dataset_row_schema_invalid');continue;}
    const key=contentKey(row);
    if(ids.has(row.id)||contents.has(key))errors.push('duplicate_row');
    ids.add(row.id);contents.add(key);langs.add(row.lang);senders.add(row.senderType);counts[row.label]++;
  }
  if(requireCoverage && (labels.some(x=>!counts[x])||languages.some(x=>!langs.has(x))||senderTypes.some(x=>!senders.has(x))))errors.push('coverage_missing');
  return {valid:errors.length===0,errors:[...new Set(errors)],rowCount:rows.length,labelCounts:counts};
}
export function readDataset(file){
  const lines=fs.readFileSync(file,'utf8').split(/\r?\n/).filter(line=>line.trim());
  return lines.map(line=>JSON.parse(line));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  try{
    const file=process.argv[2]??new URL('./out/notifications.sample.jsonl',import.meta.url);
    const result=validateDataset(readDataset(file));
    console.log(`SYNTHETIC dataset: ${result.valid?'PASS':'FAIL'}; rows=${result.rowCount}`);
    console.log(`Label counts: ${JSON.stringify(result.labelCounts)}`);
    if(!result.valid){console.error(`Validation codes: ${result.errors.join(',')}`);process.exitCode=1;}
  }catch{console.error('Dataset unreadable or invalid JSONL');process.exitCode=1;}
}

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { deduplicateRows, validateDataset, rowSchema } from './validate-dataset.mjs';

export function sampleRows(){
  const examples=[
    ['EN','urgent','family','communication','Family check-in','Please call now; I need a hand with an urgent errand.'],
    ['EN','urgent','bank_or_otp','utility','Sign-in approval','A requested sign-in approval is waiting. Review it now.'],
    ['EN','important','colleague','communication','Project review','Please review the draft before tomorrow morning meeting.'],
    ['EN','important','family','communication','Home plan','Please confirm the family travel plan this evening.'],
    ['EN','normal','friend','communication','Lunch plan','Are we meeting for lunch after class?'],
    ['EN','normal','delivery','shopping','Parcel update','Your imaginary parcel is on its way.'],
    ['EN','engagement_bait','app','social','A new reaction','Someone reacted to your fictional post.'],
    ['EN','engagement_bait','unknown','short_video','Trending clip','A new short clip is waiting in your feed.'],
    ['EN','promo','app','shopping','Weekend offer','A fictional store has a sale this weekend.'],
    ['EN','promo','delivery','shopping','Delivery discount','A made-up discount is available on the next order.'],
    ['Hinglish','urgent','family','communication','Abhi call karna','Ek zaroori kaam mein madad chahiye, abhi call kar sakte ho?'],
    ['Hinglish','urgent','bank_or_otp','utility','Sign-in request','Tumhari requested sign-in approval wait kar rahi hai; abhi check karo.'],
    ['Hinglish','important','colleague','communication','Review yaad hai?','Kal ki meeting se pehle project draft review kar dena.'],
    ['Hinglish','important','friend','communication','Study group plan','Aaj raat tak group assignment ka apna part confirm kar dena.'],
    ['Hinglish','normal','delivery','shopping','Parcel nikal gaya','Tumhara imaginary parcel raste mein hai.'],
    ['Hinglish','normal','unknown','utility','Routine reminder','Aaj ka planned break thodi der mein hai.'],
    ['Hinglish','engagement_bait','app','social','Naya reaction aaya','Tumhari fictional post pe ek aur reaction aaya hai, dekh lo.'],
    ['Hinglish','engagement_bait','friend','short_video','Ek aur clip','Ye naya short clip dekhna, feed mein aur bhi hain.'],
    ['Hinglish','promo','app','shopping','Weekend sale','Imaginary shop mein weekend sale shuru ho gayi hai.'],
    ['Hinglish','promo','unknown','shopping','Naya offer','Fictional membership par aaj ek made-up discount hai.']
  ];
  return examples.map(([lang,label,senderType,appCat,title,body],index)=>({id:`sample-${String(index+1).padStart(3,'0')}`,lang,appCat,pkg:`com.dhyan.synthetic.${appCat}`,senderType,label,hasReplyAction:['family','colleague','friend'].includes(senderType),title,body,synthetic:true}));
}

export async function generateNotifications({dryRun=false,model,count=2000,env=process.env,fetchFn=globalThis.fetch,outputPath}={}){
  let rows;
  if(dryRun){
    // Before any credential checks, prompt loading or possible fetch call.
    rows=sampleRows();
  }else{
    if(!env.OPENAI_API_KEY)throw new Error('missing_api_key');
    model=model??env.OPENAI_MODEL;
    if(typeof model!=='string'||!model.trim())throw new Error('missing_model');
    if(!Number.isInteger(count)||count<20||count>2000)throw new Error('invalid_count');
    const prompt=await fs.readFile(new URL('./prompt-template.md',import.meta.url),'utf8');
    rows=[];
    for(let offset=0;offset<count;offset+=40){
      const batchCount=Math.min(40,count-offset);
      const response=await fetchFn('https://api.openai.com/v1/responses',{
        method:'POST',redirect:'error',signal:AbortSignal.timeout(60000),
        headers:{'Content-Type':'application/json',Authorization:`Bearer ${env.OPENAI_API_KEY}`},
        body:JSON.stringify({model,store:false,input:[{role:'system',content:prompt},{role:'user',content:`Generate ${batchCount} new rows, batch starting at index ${offset}.`}],max_output_tokens:16000,
          text:{format:{type:'json_schema',name:'synthetic_notifications',strict:true,schema:{type:'object',properties:{rows:{type:'array',items:rowSchema}},required:['rows'],additionalProperties:false}}}})
      });
      if(!response.ok)throw new Error('api_request_failed');
      const result=await response.json();
      if(result.status!=='completed')throw new Error('api_response_incomplete');
      const text=(result.output??[]).filter(x=>x.type==='message').flatMap(x=>x.content??[]).filter(x=>x.type==='output_text').map(x=>x.text).join('');
      let parsed;try{parsed=JSON.parse(text);}catch{throw new Error('api_output_invalid');}
      if(!parsed||!Array.isArray(parsed.rows)||parsed.rows.length!==batchCount)throw new Error('api_row_count_invalid');
      rows.push(...parsed.rows.map((row,index)=>({...row,id:`generated-${String(offset+index+1).padStart(6,'0')}`})));
    }
  }
  rows=deduplicateRows(rows);
  if(rows.length!==(dryRun?20:count))throw new Error('deduplicated_count_mismatch');
  const validation=validateDataset(rows);
  if(!validation.valid)throw new Error('dataset_validation_failed');
  const file=outputPath??fileURLToPath(new URL(dryRun?'./out/notifications.sample.jsonl':'./out/notifications.synthetic.jsonl',import.meta.url));
  await fs.mkdir(path.dirname(file),{recursive:true});
  // Write only validated rows, never raw API responses or real notifications.
  const temporary=`${file}.${process.pid}.tmp`;
  try{await fs.writeFile(temporary,rows.map(row=>JSON.stringify(row)).join('\n')+'\n','utf8');await fs.rename(temporary,file);}
  finally{await fs.rm(temporary,{force:true});}
  return validation;
}
function parseArguments(argv){
  const options={};
  for(let i=0;i<argv.length;i++){
    if(argv[i]==='--dry-run')options.dryRun=true;
    else if(argv[i]==='--model'&&argv[i+1]&&!argv[i+1].startsWith('--'))options.model=argv[++i];
    else if(argv[i]==='--count'&&argv[i+1])options.count=Number(argv[++i]);
    else throw new Error('invalid_arguments');
  }
  return options;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  try{
    const result=await generateNotifications(parseArguments(process.argv.slice(2)));
    console.log(`SYNTHETIC dataset: wrote ${result.rowCount} deduplicated valid rows.`);
    console.log(`Label counts: ${JSON.stringify(result.labelCounts)}`);
  }catch(error){
    const safe=new Set(['missing_api_key','missing_model','invalid_count','api_request_failed','api_response_incomplete','api_output_invalid','api_row_count_invalid','conflicting_duplicate','dataset_row_schema_invalid','deduplicated_count_mismatch','dataset_validation_failed','invalid_arguments']);
    console.error(`Generation failed: ${safe.has(error?.message)?error.message:'generation_error'}`);process.exitCode=1;
  }
}

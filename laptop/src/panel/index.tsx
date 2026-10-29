import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {PipCompanion} from '../pet/PipCompanion';
import {nextBubble,type BubbleState} from '../pet/bubbles';
import type {DhyanUi,UiSnapshot} from '../shared/ui';
import taxonomy from '../../../core-spec/categories.json';
import './style.css';
declare global {interface Window {dhyan:DhyanUi}}
function App(){
 const [data,setData]=useState<UiSnapshot|null>(null),[unavailable,setUnavailable]=useState(false),[bubble,setBubble]=useState<string|null>(null),[notice,setNotice]=useState('');
 const [task,setTask]=useState('coding'),[plannedMin,setPlannedMin]=useState(120),[strict,setStrict]=useState(true);
 const [processLabel,setProcessLabel]=useState(''),[labelNotice,setLabelNotice]=useState(''),[labelCategory,setLabelCategory]=useState('games');
 const cooldown=useRef<BubbleState>({lastShown:{}}),hitbox=useRef<HTMLDivElement>(null);
 const panel=new URLSearchParams(location.search).get('view')==='panel';
 useEffect(()=>{let active=true;const poll=()=>void window.dhyan.snapshot().then(value=>{if(!active)return;setData(value);setUnavailable(false);const result=nextBubble(value.pet.behavior,cooldown.current,Date.now());cooldown.current=result.state;setBubble(result.bubble);}).catch(()=>{if(active)setUnavailable(true);});poll();const timer=setInterval(poll,2000);return ()=>{active=false;clearInterval(timer);};},[]);
 useEffect(()=>{if(panel)return;const element=hitbox.current;if(!element)return;const enter=()=>window.dhyan.setIgnoreMouseEvents(false),leave=()=>window.dhyan.setIgnoreMouseEvents(true);element.addEventListener('mouseenter',enter);element.addEventListener('mouseleave',leave);return ()=>{element.removeEventListener('mouseenter',enter);element.removeEventListener('mouseleave',leave);};},[panel]);
 const remote=data?.inputs.remote,remoteFresh=remote?.connected&&Date.now()>=remote.ts&&Date.now()-remote.ts<30000;
 const declared=data?.inputs.declaredSession;
 if(!panel)return <div id="pip-hitbox" ref={hitbox}><PipCompanion behavior={unavailable?null:data?.pet.behavior??null} speechBubble={unavailable?'Data unavailable':bubble} heldCount={unavailable?null:data?.pet.heldCount??null} onClick={()=>window.dhyan.openPanel()}/>{data?.demo&&<small>DEV SIMULATION</small>}</div>;
 return <main><h1>Dhyan</h1><p>Schema {window.dhyan.schemaVersion}</p>{data?.demo&&<p role="alert">DEV SIMULATION — not observed activity</p>}
 <section><h2>Laptop</h2><p aria-live="polite">{unavailable?'Data unavailable':data?.pet.label??'No data yet'}</p><p>rule-based, not calibrated</p>{data?.inputs.storagePaused&&<p role="alert">Storage paused. Recent events may exist only in memory.</p>}{data&&<p>Events written: {data.diagnostics.eventsWritten??'Unavailable'}</p>}
 <h3>Today’s focus time</h3>{data?.focus.minutes==null?<p>No data: observed focus history is unavailable.</p>:<p>{data.focus.minutes.toFixed(1)} minutes — observed, rule-based intervals</p>}<small>{data?.focus.label}</small></section>
 <section><h2>Phone and held items</h2><p>{!remote?.connected?'Not connected to phone':!remoteFresh?'Phone data is stale':'Connected to phone'}</p>{remoteFresh&&(remote?.heldCount===0?<p>None held</p>:<><p>{remote?.heldCount} held</p><ul>{remote?.heldItems.map(item=><li key={item.heldId}>{item.appCat} · {item.status.toLowerCase().replaceAll('_',' ')}</li>)}</ul><p>Show now is pending phone sync.</p></>)}</section>
 <section><h2>Declare session</h2><p>{declared?`Local declared task: ${declared.task}, ${declared.plannedMin} minutes, ${declared.strict?'strict':'flexible'}`:'No local session declared'}</p><form onSubmit={event=>{event.preventDefault();void window.dhyan.declareSession({task,plannedMin,strict}).then(result=>setNotice(result.message)).catch(()=>setNotice('Could not declare session.'));}}>
 <label>Task <select value={task} onChange={e=>setTask(e.target.value)}>{['coding','studying','writing','gaming','browsing','unknown'].map(value=><option key={value}>{value}</option>)}</select></label><label>Minutes <input type="number" min="5" max="480" value={plannedMin} onChange={e=>setPlannedMin(Number(e.target.value))}/></label><label><input type="checkbox" checked={strict} onChange={e=>setStrict(e.target.checked)}/>Strict</label><button>Declare locally</button></form><p aria-live="polite">{notice}</p></section>
 <section><h2>Label a process once</h2><p>Known process list plus your label only. Communication apps count as work only when you label them as coding, docs or learning. No paths are read.</p><form onSubmit={event=>{event.preventDefault();void window.dhyan.labelProcess(processLabel,labelCategory).then(ok=>setLabelNotice(ok?'Label recorded; it takes effect after a window change.':'Label already set or process name invalid.')).catch(()=>setLabelNotice('Could not store the label.'));}}><label>Process name only<input value={processLabel} maxLength={100} placeholder="Game.exe" onChange={e=>setProcessLabel(e.target.value)}/></label><label>Category<select value={labelCategory} onChange={e=>setLabelCategory(e.target.value)}>{taxonomy.categories.map(category=><option key={category}>{category}</option>)}</select></label><button>Save label once</button></form><p>{labelNotice}</p></section>
 <section><h2>Pairing</h2><p>{data?.pairing.label??'Not paired'}</p><p>Sync pending. Local declarations do not control your phone yet.</p></section>
 <section><h2>What-if</h2><p>Pending phone sync. No estimate is available.</p></section>
 <section><h2>Extensions</h2><p>Status unavailable — localhost ingest is pending Stage 7C.</p></section>
 </main>;
}
createRoot(document.getElementById('root')!).render(<App/>);

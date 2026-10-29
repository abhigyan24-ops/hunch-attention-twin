import React from 'react';
import type {PipBehavior} from './petStateMachine';
export function PipCompanion({behavior,speechBubble,heldCount,onClick}:{behavior:PipBehavior|null;speechBubble:string|null;heldCount:number|null;onClick:()=>void}){
 return <div className="pip-container" data-behavior={behavior??'neutral'}>
  {speechBubble&&<p className="pip-bubble" aria-live="polite">{speechBubble}</p>}
  <button className={`pip-avatar pip-${behavior??'neutral'}`} aria-label="Open Dhyan panel" onClick={onClick}>
   <svg viewBox="0 0 100 100" width="84" height="84" aria-hidden="true"><path d="M30 30 20 10 44 25M70 30 80 10 56 25" fill="#8ec9db"/><rect x="20" y="24" width="60" height="62" rx="25" fill="#233e51" stroke="#8ec9db" strokeWidth="3"/>
   {behavior==='deep_focus'?<path d="M32 50q7 6 14 0M54 50q7 6 14 0" fill="none" stroke="white" strokeWidth="3"/>:<><circle cx="38" cy="50" r="4" fill="white"/><circle cx="62" cy="50" r="4" fill="white"/></>}
   <path d={behavior==='distracted'?'M40 72q10-8 20 0':behavior==='fatigued'?'M45 66q5 14 10 0Z':'M40 66q10 8 20 0'} fill="none" stroke="white" strokeWidth="2"/>{behavior==='second_screen_overlap'&&<path d="m55 39 14-4" stroke="white" strokeWidth="3"/>}</svg>
  </button>{heldCount!==null&&heldCount>0&&<span className="held-badge">{heldCount} held</span>}
 </div>;
}

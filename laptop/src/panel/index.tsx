import React,{useEffect,useRef} from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';
declare global {interface Window {dhyan:{schemaVersion:string;setIgnoreMouseEvents(ignore:boolean):void}}}
function App(){
 const hitbox=useRef<HTMLDivElement>(null);
 useEffect(()=>{const el=hitbox.current;if(!el)return;const enter=()=>window.dhyan.setIgnoreMouseEvents(false),leave=()=>window.dhyan.setIgnoreMouseEvents(true);el.addEventListener('mouseenter',enter);el.addEventListener('mouseleave',leave);return()=>{el.removeEventListener('mouseenter',enter);el.removeEventListener('mouseleave',leave);};},[]);
 return <div id="pip-hitbox" ref={hitbox}><h1>Dhyan Laptop Scaffold</h1><p>Schema v{window.dhyan.schemaVersion}</p><p>Collectors and Pip animations pending.</p></div>;
}
createRoot(document.getElementById('root')!).render(<App/>);

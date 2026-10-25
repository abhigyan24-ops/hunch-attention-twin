// Only bucket IDs are printed. Never request event endpoints or emit API bodies.
try {
 const info=await fetch('http://localhost:5600/api/0/info',{signal:AbortSignal.timeout(3000)});
 if(!info.ok)throw new Error();
 await info.arrayBuffer();
 const response=await fetch('http://localhost:5600/api/0/buckets/',{signal:AbortSignal.timeout(3000)});
 if(!response.ok)throw new Error();const buckets=await response.json();
 if(!buckets || typeof buckets!=='object' || Array.isArray(buckets))throw new Error();
 const ids=Object.keys(buckets);for(const id of ids)console.log(id);
 if(!ids.some(id=>/^aw-watcher-window[_-]/.test(id)) || !ids.some(id=>/^aw-watcher-afk[_-]/.test(id)))process.exitCode=1;
} catch {process.exitCode=1;}

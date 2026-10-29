/** Privileged IPC is available only to the exact renderer document loaded by Dhyan. */
export function isTrustedUiUrl(candidate:string,rendererFileUrl:string,developmentUrl?:string):boolean {
 try {
  const base=new URL(developmentUrl??rendererFileUrl);base.hash='';
  const panel=new URL(base);panel.searchParams.set('view','panel');
  const actual=new URL(candidate);if(actual.username||actual.password)return false;actual.hash='';
  return actual.href===base.href||actual.href===panel.href;
 }catch{return false;}
}

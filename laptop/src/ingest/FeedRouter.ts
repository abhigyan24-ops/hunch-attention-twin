import type {LaptopPipeline} from '../main/LaptopPipeline';import {VsCodeFeed} from '../adapters/VsCodeFeed';import {BrowserFeed} from '../adapters/BrowserFeed';import type {ClientKind,FeedMessage} from './types';
export class FeedRouter {
 readonly vscode:VsCodeFeed;readonly browser:BrowserFeed;
 constructor(private pipeline:LaptopPipeline,now=Date.now){this.vscode=new VsCodeFeed(now);this.browser=new BrowserFeed(now);pipeline.setFeedProvider(()=>{const vscode=this.vscode.snapshot(),browser=this.browser.snapshot();return {vscode:vscode.online&&vscode.state?{state:vscode.state,windowFocused:vscode.windowFocused,editsPerMin:vscode.editsPerMin}:null,browser:browser.tab};});}
 connection(client:ClientKind,connected:boolean){const feed=client==='vscode'?this.vscode:this.browser;if(connected)feed.connect();else feed.disconnect();this.refreshStuck();}
 private refreshStuck(){const state=this.vscode.snapshot();this.pipeline.setStuckEvidence(state.online&&state.windowFocused&&state.state==='stuck');}
 accept(client:ClientKind,message:FeedMessage){const feed=client==='vscode'?this.vscode:this.browser;feed.accept(message);this.refreshStuck();
  if(message.kind==='edit_burst')return this.pipeline.injectFeedEvent({kind:'edit',ts:message.ts,editsPerMin:message.editsPerMin,saves:message.saves,errors:message.errors,warnings:message.warnings});
  if(message.kind==='resume_first_edit')return this.pipeline.injectFeedEvent({kind:'resume',ts:message.ts,delayMs:message.delayMs});
  if(message.kind==='tab_category')return this.pipeline.injectFeedEvent({kind:'tab',ts:message.ts,domainCat:message.domainCat,surface:message.surface,...(message.videoState!==undefined?{videoState:message.videoState}:{}),...(message.scrollCount!==undefined?{scrollCount:message.scrollCount}:{}),tabActive:message.tabActive});
  return true;
 }
 snapshot(){return {vscode:this.vscode.snapshot(),browser:this.browser.snapshot()};}
}

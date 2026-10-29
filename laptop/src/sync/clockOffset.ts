export class ClockOffset{
 private samples:Array<{rtt:number;offset:number}>=[];
 add(t0:number,t1:number,t2:number,t3:number){const rtt=(t3-t0)-(t2-t1),offset=((t1-t0)+(t2-t3))/2;if(![t0,t1,t2,t3,rtt,offset].every(Number.isFinite)||rtt<0||rtt>2000||t2<t1)return false;this.samples.push({rtt,offset});if(this.samples.length>8)this.samples.shift();return true;}
 get estimated(){return this.samples.length===0;}
 get peerMinusLocal(){return this.samples.length?this.samples.reduce((a,b)=>a.rtt<=b.rtt?a:b).offset:0;}
 get offsetToReceiver(){return this.peerMinusLocal===0?0:-this.peerMinusLocal;}
 reset(){this.samples=[];}
}

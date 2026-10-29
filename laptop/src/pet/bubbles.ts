import type {PipBehavior} from './petStateMachine';
export const BUBBLE_COOLDOWN_MS=600000;
const templates:Record<PipBehavior,string>={deep_focus:'Working quietly alongside you.',held:'Items are held on your phone.',drifting:'Would a short pause help?',distracted:'Ready to return to your task?',recovering:'Welcome back.',fatigued:'Would you like a break?',second_screen_overlap:'Both screens are busy.'};
export interface BubbleState {lastShown:Partial<Record<PipBehavior,number>>}
export function nextBubble(behavior:PipBehavior|null,previous:BubbleState,nowMs:number):{bubble:string|null;state:BubbleState}{
 if(!behavior)return {bubble:null,state:previous};const last=previous.lastShown[behavior];
 if(last!==undefined&&(nowMs<last||nowMs-last<BUBBLE_COOLDOWN_MS))return {bubble:null,state:previous};
 return {bubble:templates[behavior],state:{lastShown:{...previous.lastShown,[behavior]:nowMs}}};
}

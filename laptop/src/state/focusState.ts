export type LaptopState='flowing'|'stuck'|'idle_open'|'browsing'|'gaming'|'away'|'offline';
// TODO 12.4/25.9: evaluate category/count windows; absent telemetry is offline.
export function currentFocusState():LaptopState{return 'offline';}

import categories from '../../../core-spec/categories.json';
export type Category = typeof categories.categories[number];
const processMap=new Map(Object.entries(categories.windowsProcesses).map(([key,value])=>[key.toLowerCase(),value]));
const domainMap=new Map(Object.entries(categories.domains).map(([key,value])=>[key.toLowerCase(),value]));
export function categorizeProcess(proc:string):Category{return processMap.get(proc.toLowerCase()) ?? 'unknown';}
export function categorizeDomain(domain:string):Category{return domainMap.get(domain.toLowerCase()) ?? 'unknown';}

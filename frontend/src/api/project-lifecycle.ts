import { apiRequest } from './client'
export interface LifecycleNode {type:string;referenceId:number;title:string;date:string;scheduleStatus:'NORMAL'|'DUE_SOON'|'OVERDUE';businessStatus:string;description:string|null}
export function listProjectLifecycle(projectId:number,from:string,to:string):Promise<LifecycleNode[]>{const q=new URLSearchParams({from,to});return apiRequest(`/api/v1/projects/${projectId}/lifecycle?${q}`)}

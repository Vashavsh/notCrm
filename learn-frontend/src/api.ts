import type {Session} from './types';
let csrf='';
let pendingWrites:Promise<unknown>=Promise.resolve();
export class ApiError extends Error { constructor(message:string,public status:number,public code?:string){super(message);} }
export async function api<T>(path:string,body?:unknown,method?:string):Promise<T>{
  const verb=method||(body===undefined?'GET':'POST');
  if(verb==='GET')return request<T>(path,body,verb);
  // Keep autosaves and submitted answers in order, including their responses.
  const result=pendingWrites.then(()=>request<T>(path,body,verb));
  pendingWrites=result.then(()=>undefined,()=>undefined);
  return result;
}
async function request<T>(path:string,body:unknown,method:string):Promise<T>{
  const isForm=body instanceof FormData;
  const response=await fetch(`/api${path}`,{method,credentials:'same-origin',headers:{...(body!==undefined&&!isForm?{'Content-Type':'application/json'}:{}),...(body!==undefined?{'X-CSRFToken':csrf}:{})},body:body===undefined?undefined:isForm?body:JSON.stringify(body)});
  let data:any;
  try{data=await response.json();}catch{throw new ApiError('Сервер не ответил. Проверьте подключение и попробуйте ещё раз.',response.status);}
  if(!response.ok){
    if((response.status===401&&path!=='/login')||data.code==='session_revoked')window.dispatchEvent(new Event('academy:session-expired'));
    throw new ApiError(data.error||'Не удалось выполнить действие. Попробуйте ещё раз.',response.status,data.code);
  }
  if(data.csrfToken)csrf=data.csrfToken;
  return data as T;
}
export const getSession=()=>api<Session>('/session');

import { fetchApi } from '../../services/api';
import { Access } from '../../auth';
import { PaginationInfo } from '../../utils'

function paginationParams(pagination: PaginationInfo) {
    return `page=${pagination.page}&limit=${pagination.perPage}&sort=${pagination.sort}&direction=${pagination.direction}`
}

const base = "/api/run"
const endPoints = {

    getRun: (runId: number, token?: string) => `${base}/${runId}${token ? '?token=' + token : ''}`,
    addRun: () => `${base}/`,
    list: (query: string, matchAll: boolean, roles: string, pagination: PaginationInfo, trashed: boolean) => `${base}/list?${paginationParams(pagination)}&query=${query}&matchAll=${matchAll}&roles=${roles}&trashed=${trashed}`,
    suggest: (query: string, roles: string) => `${base}/autocomplete?query=${query}&roles=${roles}`,
    js: (runId: number, token?: string) => `${base}/${runId}/js`,
    listByTest: (testId: number, pagination: PaginationInfo, trashed?: boolean) => `${base}/list/${testId}?${paginationParams(pagination)}&trashed=${!!trashed}`,
    resetToken: (runId: number) => `${base}/${runId}/resetToken`,
    dropToken: (runId: number) => `${base}/${runId}/dropToken`,
    updateAccess: (runId: number, owner: string, access: Access) => `${base}/${runId}/updateAccess?owner=${owner}&access=${access}`,
    trash: (runId: number, isTrashed: boolean) => `${base}/${runId}/trash?isTrashed=${isTrashed}`,
    description: (runId: number) => `${base}/${runId}/description`
}

export const get = (id: number, token?: string, js?: any) => {
    if(typeof js === "undefined" || js === null){
        return fetchApi(endPoints.getRun(id, token),null,'get');
    }else{
        return fetchApi(endPoints.js(id, token),js,'post');
    }
}

export const byTest = (id: number, pagination: PaginationInfo, trashed?: boolean) => fetchApi(endPoints.listByTest(id, pagination, trashed), null, 'get');

export const list = (query: string, matchAll: boolean, roles: string, pagination: PaginationInfo, trashed: boolean) => fetchApi(endPoints.list(query, matchAll, roles, pagination, trashed),null,'get')

export const suggest = (query: string, roles: string) => fetchApi(endPoints.suggest(query, roles), null, 'get');

export const resetToken = (id: number) => fetchApi(endPoints.resetToken(id), null, 'post', {}, 'text');

export const dropToken = (id: number) => fetchApi(endPoints.dropToken(id), null, 'post');

export const updateAccess = (id: number, owner: string, access: Access) => {
   // TODO: fetchival does not support form parameters, it tries to JSONify everything
   return fetchApi(endPoints.updateAccess(id, owner, access), null, 'post', {}, 'response')
//                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
//                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

export const trash = (id: number, isTrashed: boolean) => fetchApi(endPoints.trash(id, isTrashed), null, 'post', {}, 'text')

export const updateDescription = (id: number, description: string) => {
    return fetchApi(endPoints.description(id), description, 'post', { 'Content-Type' : 'text/plain' }, 'response')
 }
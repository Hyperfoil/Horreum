import { fetchApi } from '../../services/api';

const base = "/api/run"
const endPoints = {

    getRun: (runId: number, token: string) => `${base}/${runId}${token ? '?token=' + token : ''}`,
    addRun: () => `${base}/`,
    listAll: () => `${base}/list/`,
    filter: (query: string, matchAll: boolean, roles: string) => `${base}/filter?query=${query}&matchAll=${matchAll}&roles=${roles}`,
    suggest: (query: string, roles: string) => `${base}/autocomplete?query=${query}&roles=${roles}`,
    js: (runId: number, token: string) => `${base}/${runId}/js`,
    listByTest: (testId: number) => `${base}/list/${testId}`,
    resetToken: (runId: number) => `${base}/${runId}/resetToken`,
    dropToken: (runId: number) => `${base}/${runId}/dropToken`,
    updateAccess: (runId: number, owner: string, access: string) => `${base}/${runId}/updateAccess?owner=${owner}&access=${access}`,
}

export const all = () => {
    return fetchApi(endPoints.listAll(),null,'get');
}

export const get = (id: number, token: string, js?: any) => {
    if(typeof js === "undefined" || js === null){
        return fetchApi(endPoints.getRun(id, token),null,'get');
    }else{
        return fetchApi(endPoints.js(id, token),js,'post');
    }
}

export const byTest = (id: number) => fetchApi(endPoints.listByTest(id), null, 'get');

export const filter = (query: string, matchAll: boolean, roles: string) => fetchApi(endPoints.filter(query, matchAll, roles),null,'get')

export const suggest = (query: string, roles: string) => fetchApi(endPoints.suggest(query, roles), null, 'get');

export const resetToken = (id: number) => fetchApi(endPoints.resetToken(id), null, 'post', {}, 'text');

export const dropToken = (id: number) => fetchApi(endPoints.dropToken(id), null, 'post');

export const updateAccess = (id: number, owner: string, access: string) => {
   // TODO: fetchival does not support form parameters, it tries to JSONify everything
   return fetchApi(endPoints.updateAccess(id, owner, access), null, 'post', {}, 'response')
//                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
//                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

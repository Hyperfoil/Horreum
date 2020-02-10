import { fetchApi } from '../../services/api';

const base = "/api/run"
const endPoints = {
    
    getRun: (runId, token) => `${base}/${runId}${token ? '?token=' + token : ''}`,
    addRun: () => `${base}/`,
    listAll: () => `${base}/list/`,
    filter: (query, matchAll, roles) => `${base}/filter?query=${query}&matchAll=${matchAll}&roles=${roles}`,
    suggest: (query, roles) => `${base}/autocomplete?query=${query}&roles=${roles}`,
    js: runId => `${base}/${runId}/js`,
    listByTest: (testId) => `${base}/list/${testId}`,
    resetToken: (runId) => `${base}/${runId}/resetToken`,
    dropToken: (runId) => `${base}/${runId}/dropToken`,
    updateAccess: (runId, owner, access) => `${base}/${runId}/updateAccess?owner=${owner}&access=${access}`
}

export const all = () => {
    return fetchApi(endPoints.listAll(),null,'get');
}

export const get = (id, token, js) => {
    if(typeof js === "undefined" || js === null){   
        return fetchApi(endPoints.getRun(id, token),null,'get');
    }else{
        return fetchApi(endPoints.js(id, token),js,'post');
    }
}

export const byTest = (id) => fetchApi(endPoints.listByTest(id), null, 'get');

export const filter = (query, matchAll, roles) => fetchApi(endPoints.filter(query, matchAll, roles),null,'get')

export const suggest = (query, roles) => fetchApi(endPoints.suggest(query, roles), null, 'get');

export const resetToken = (id) => fetchApi(endPoints.resetToken(id), null, 'post', {}, 'text');

export const dropToken = (id) => fetchApi(endPoints.dropToken(id), null, 'post');

export const updateAccess = (id, owner, access) => {
   // TODO: fetchival does not support form parameters, it tries to JSONify everything
   return fetchApi(endPoints.updateAccess(id, owner, access), null, 'post', {}, 'response')
//                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
//                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}
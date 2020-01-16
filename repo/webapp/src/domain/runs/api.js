import { fetchApi } from '../../services/api';

const base = "/api/run"
const endPoints = {
    
    getRun: runId => `${base}/${runId}/`,
    addRun: () => `${base}/`,
    listAll: ()=> `${base}/list/`,
    filter: (query, matchAll) => `${base}/filter?query=${query}&matchAll=${matchAll}`,
    suggest: (query) => `${base}/autocomplete?query=${query}`,
    js: runId => `${base}/${runId}/js`,
    listByTest: testId => `${base}/list/${testId}`

}

export const all = () => {
    return fetchApi(endPoints.listAll(),null,'get');

}
export const get = (id,js) => {
    if(typeof js === "undefined" || js === null){   
        return fetchApi(endPoints.getRun(id),null,'get');
    }else{
        return fetchApi(endPoints.js(id),js,'post');
    }
}
export const byTest = (id,payload) => fetchApi(endPoints.listByTest(id),payload,'post');

export const filter = (query, matchAll) => fetchApi(endPoints.filter(query, matchAll),null,'get')

export const suggest = (query) => fetchApi(endPoints.suggest(query), null, 'get');
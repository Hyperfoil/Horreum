import { fetchApi } from '../../services/api';

const base = "/api/test"
const endPoints = {
    base: ()=>`${base}`,
    crud: (id)=>`${base}/${id}`,
    schema: (id)=>`${base}/${id}/schema`,
    view: (id)=>`${base}/${id}/view`,
    summary: ()=>`${base}/summary`,
    resetToken: (id) => `${base}/${id}/resetToken`,
    dropToken: (id) => `${base}/${id}/dropToken`,
    updateAccess: (id, owner, access) => `${base}/${id}/updateAccess?owner=${owner}&access=${access}`,
}

export const all = () => {
    return fetchApi(endPoints.list(),null,'get');
}
export const view = (id) => {
    return fetchApi(endPoints.view(id),null,'get')
}
export const get = (id) => {
    return fetchApi(endPoints.crud(id),null,'get')
}
export const summary = () => {
    return fetchApi(endPoints.summary(),null,'get');
}
export const send = (test) => {
    return fetchApi(endPoints.base(),test,'post')
}

export const resetToken = (id) => fetchApi(endPoints.resetToken(id), null, 'post', {}, 'text');

export const dropToken = (id) => fetchApi(endPoints.dropToken(id), null, 'post');

export const updateAccess = (id, owner, access) => {
   // TODO: fetchival does not support form parameters, it tries to JSONify everything
   return fetchApi(endPoints.updateAccess(id, owner, access), null, 'post', {}, 'response')
//                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
//                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

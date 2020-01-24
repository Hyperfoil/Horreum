import { fetchApi } from '../../services/api';
const base = "/api/schema"
const endPoints = {
    base: ()=>`${base}`,
    crud:  (id)=> `${base}/${id}/`,
    resetToken: (id) => `${base}/${id}/resetToken`,
    dropToken: (id) => `${base}/${id}/dropToken`,
    updateAccess: (id, owner, access) => `${base}/${id}/updateAccess?owner=${owner}&access=${access}`
}
export const all = ()=>{
    return fetchApi(endPoints.base(),null,'get');
}
export const add = (payload)=>{
    return fetchApi(endPoints.base(),payload,'post');
}
export const getByName = (name)=>{
    return fetchApi(endPoints.crud(name),null,'get');
}
export const getById = (id)=>{
    return fetchApi(endPoints.crud(id),null,'get');
}

export const resetToken = (id) => fetchApi(endPoints.resetToken(id), null, 'post', {}, 'text');

export const dropToken = (id) => fetchApi(endPoints.dropToken(id), null, 'post');

export const updateAccess = (id, owner, access) => {
   // TODO: fetchival does not support form parameters, it tries to JSONify everything
   return fetchApi(endPoints.updateAccess(id, owner, access), null, 'post', {}, 'response')
//                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
//                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

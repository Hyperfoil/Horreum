import { fetchApi } from '../../services/api';
import { Test } from './reducers';

const base = "/api/test"
const endPoints = {
    base: ()=>`${base}`,
    crud: (id: number)=>`${base}/${id}`,
    schema: (id: number)=>`${base}/${id}/schema`,
    view: (id: number)=>`${base}/${id}/view`,
    summary: ()=>`${base}/summary`,
    resetToken: (id: number) => `${base}/${id}/resetToken`,
    dropToken: (id: number) => `${base}/${id}/dropToken`,
    updateAccess: (id: number, owner: string, access: string) => `${base}/${id}/updateAccess?owner=${owner}&access=${access}`,
}

export const view = (id: number) => {
    return fetchApi(endPoints.view(id),null,'get')
}
export const get = (id: number) => {
    return fetchApi(endPoints.crud(id),null,'get')
}
export const summary = () => {
    return fetchApi(endPoints.summary(),null,'get');
}
export const send = (test: Test) => {
    return fetchApi(endPoints.base(),test,'post')
}

export const resetToken = (id: number) => fetchApi(endPoints.resetToken(id), null, 'post', {}, 'text');

export const dropToken = (id: number) => fetchApi(endPoints.dropToken(id), null, 'post');

export const updateAccess = (id: number, owner: string, access: string) => {
   // TODO: fetchival does not support form parameters, it tries to JSONify everything
   return fetchApi(endPoints.updateAccess(id, owner, access), null, 'post', {}, 'response')
//                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
//                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

export const deleteTest = (id: number) => fetchApi(endPoints.crud(id), null, 'delete')

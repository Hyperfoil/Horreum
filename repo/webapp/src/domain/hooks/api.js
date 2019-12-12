import { fetchApi } from '../../services/api';

const base = "/api/hook"
const endPoints = {
    base: ()=>`${base}`,
    crud:  (id)=> `${base}/${id}/`,
    list: ()=> `${base}/list/`,
}

export const all = () => {
    return fetchApi(endPoints.list(),null,'get');

}
export const add = (payload) => {
    return fetchApi(endPoints.base(),payload,'post')
}
export const get = (id) => {
    return fetchApi(endPoints.crud(id),null,'get');
}
export const remove = (id) => {
    return fetchApi(endPoints.crud(id),null,'delete');
}
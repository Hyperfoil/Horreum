import { fetchApi } from '../../services/api';
const base = "/api/schema"
const endPoints = {
    base: ()=>`${base}`,
    crud:  (id)=> `${base}/${id}/`,
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
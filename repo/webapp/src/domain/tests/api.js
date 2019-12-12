import { fetchApi } from '../../services/api';

const base = "/api/test"
const endPoints = {
    base: ()=>`${base}`,
    crud: (id)=>`${base}/${id}`,
    schema: (id)=>`${base}/${id}/schema`,
    summary: ()=>'/api/sql?q=select test.id,test.name,test.description,count(run.id) as count,test.schema is not null as hasschema from test left join run on run.testId = test.id group by test.id'
}

export const all = () => {
    return fetchApi(endPoints.list(),null,'get');
}
export const get = (id) => {
    return fetchApi(endPoints.crud(id),null,'get')
}
export const summary = () => {
    return fetchApi(endPoints.summary(),null,'get');
}
export const send = (test) => {
    return fetchApi(endPoints.base(),test,'post');
}

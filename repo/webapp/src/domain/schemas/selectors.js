import store from '../../store';

const emptySchema = {name:"",description:"",schema:{
    "$schema": "http://json-schema.org/draft-07/schema"
}}

export const all = () =>{
    let list = [...store.getState().schemas.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const getById = (id)=> ()=>{
    const rtrn = store.getState().schemas.byId.get(`${id}`,emptySchema);
    return rtrn;
}
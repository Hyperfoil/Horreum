const emptySchema = {name:"",description:"",schema:{
    "$schema": "http://json-schema.org/draft-07/schema"
}}

export const all = state =>{
    let list = [...state.schemas.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const getById = (id)=> state => {
    const rtrn = state.schemas.byId.get(`${id}`,emptySchema);
    return rtrn;
}
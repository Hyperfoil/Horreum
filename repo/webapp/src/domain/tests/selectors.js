import store from '../../store';

const emptyTest = {name:"",description:"",schema:{
    "$schema": "http://json-schema.org/draft-07/schema"
}}

export const all = () =>{
    let list = [...store.getState().tests.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id)=> ()=>{
    const rtrn = store.getState().tests.byId.get("t"+id,emptyTest);
    return rtrn;
}
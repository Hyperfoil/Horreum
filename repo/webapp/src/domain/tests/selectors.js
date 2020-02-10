import store from '../../store';

const emptyTest = {
    name:"",
    description:"",
}

export const all = () =>{
    let list = [...store.getState().tests.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id)=> ()=>{
    const rtrn = store.getState().tests.byId.get("t"+id,emptyTest);
    return rtrn;
}
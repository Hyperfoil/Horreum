import store from '../../store';

export const all = () =>{
    let list = [...store.getState().schemas.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const getById = (id)=> ()=>{
    const rtrn = store.getState().schemas.byId.get(`${id}`,{});
    return rtrn;
}
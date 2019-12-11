import store from '../../store';

export const all = () =>{
    let list = [...store.getState().hooks.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id)=> ()=>{
    const rtrn = store.getState().hooks.byId.get(`${id}`,{damn:"sorry"});
    return rtrn;
}
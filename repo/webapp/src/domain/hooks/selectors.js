export const all = state => {
    let list = [...state.hooks.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id) => state => {
    const rtrn = state.hooks.byId.get(`${id}`,{});
    return rtrn;
}
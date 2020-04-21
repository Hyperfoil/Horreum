export const all = state => {
    if (!state.hooks.byId) {
        return false
    }
    let list = [...state.hooks.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id) => state => {
    if (!state.hooks.byId) {
        return false
    }
    const rtrn = state.hooks.byId.get(`${id}`,{});
    return rtrn;
}
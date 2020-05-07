const emptyTest = {
    name:"",
    description:"",
}

export const isLoading = state => state.tests.loading

export const all = state => {
    if (!state.tests.byId) {
        return false
    }
    let list = [...state.tests.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}

export const get = (id)=> state => {
    if (!state.tests.byId) {
        return false
    }
    const rtrn = state.tests.byId.get("t"+id,emptyTest);
    return rtrn;
}
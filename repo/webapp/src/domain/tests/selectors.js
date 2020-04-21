const emptyTest = {
    name:"",
    description:"",
}

export const all = state => {
    let list = [...state.tests.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id)=> state => {
    const rtrn = state.tests.byId.get("t"+id,emptyTest);
    return rtrn;
}
import { State } from '../../store'

const emptySchema = {name:"",description:"",schema:{
    "$schema": "http://json-schema.org/draft-07/schema#"
}}

export const all = (state: State) => {
    if (!state.schemas.byId) {
        return false
    }
    let list = [...state.schemas.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;
}
export const getById = (id: number) => (state: State) => {
    if (!state.schemas.byId) {
        return false
    }
    const rtrn = state.schemas.byId.get(`${id}`,emptySchema);
    return rtrn;
}
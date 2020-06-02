import { State } from '../../store'

export const all = (state: State) => {
    if (!state.hooks.byId) {
        return false
    }
    let list = [...state.hooks.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;
}
export const get = (id: number) => (state: State) => {
    if (!state.hooks.byId) {
        return false
    }
    const rtrn = state.hooks.byId.get(`${id}`,{});
    return rtrn;
}
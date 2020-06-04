import { State } from '../../store'

export const isLoading = (state: State) => state.tests.loading

export const all = (state: State) => {
    if (!state.tests.byId) {
        return false
    }
    let list = [...state.tests.byId.values()]
    list.sort((a,b)=>a.id - b.id);
    return list;
}

export const get = (id: number)=> (state: State) => {
    if (!state.tests.byId) {
        return false
    }
    return state.tests.byId.get("t"+id);
}
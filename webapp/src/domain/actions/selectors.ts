import { State } from "../../store"

export const all = (state: State) => {
    if (!state.actions.byId) {
        return false
    }
    const list = [...state.actions.byId.values()]
    list.sort((a, b) => a.id - b.id)
    return list
}
export const get = (id: number) => (state: State) => {
    if (!state.actions.byId) {
        return false
    }
    const rtrn = state.actions.byId.get(`${id}`, {})
    return rtrn
}

import { State } from "../../store"

export const all = (state: State) => {
    if (!state.schemas.byId) {
        return false
    }
    const list = [...state.schemas.byId.values()]
    list.sort((a, b) => a.id - b.id)
    return list
}
export const getById = (id: number) => (state: State) => {
    return state.schemas.byId?.get(`${id}`)
}

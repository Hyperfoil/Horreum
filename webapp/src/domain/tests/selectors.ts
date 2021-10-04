import { State } from "../../store"

export const isLoading = (state: State) => state.tests.loading

export const all = (state: State) => {
    if (!state.tests.byId) {
        return false
    }
    let list = [...state.tests.byId.values()]
    list.sort((a, b) => a.id - b.id)
    state.tests.watches.forEach((watching, id) => {
        const test = list.find(t => t.id === id)
        if (test) {
            test.watching = watching
        }
    })
    return list
}

export const get = (id: number) => (state: State) => {
    if (!state.tests.byId) {
        return false
    }
    return state.tests.byId.get(id)
}

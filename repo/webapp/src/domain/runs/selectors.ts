import { State } from '../../store'

export const isLoading = (state: State) => state.runs.loading

export const all = (trashed: boolean) => (state: State) => {
    if (!state.runs.byId) {
        return false
    }
    let list = [...state.runs.byId.values()].filter(run => trashed || !run.trashed)
    list.sort((a,b)=>a.start - b.start);
    return list;
}
export const testRuns = (id: number, trashed: boolean) => (state: State) => {
    if (!state.runs.byTest) {
        return false
    }
    let list = [...state.runs.byTest.get(id)?.values()].filter(run => trashed || !run.trashed)
    list.sort((a,b)=>a.id - b.id);
    console.log(list)
    return list;
}
export const get = (id: string) => (state: State) =>{
    if (!state.runs.byId) {
        return false
    }
    return state.runs.byId.get(id) || false;
}
export const filter = (trashed: boolean) => (state: State) => {
   const filteredIds = state.runs.filteredIds
   if (filteredIds == null) {
      return all(trashed)(state);
   }
   const byId = state.runs.byId
   if (!byId) {
      return false
   }
   let list = [...byId.filter((v, k) => filteredIds.includes(k)).values()].filter(run => trashed || !run.trashed)
   list.sort((a,b)=>a.id - b.id);
   return list;
}

export const isFetchingSuggestions = (state: State) => {
   let suggestQuery = state.runs.suggestQuery
   return suggestQuery.length > 0;
}
export const suggestQuery = (state: State) => {
   let suggestQuery = state.runs.suggestQuery
   // Actually when this is called the suggestQuery.length should be <= 1
   return suggestQuery.length === 0 ? null : suggestQuery[suggestQuery.length - 1]
}

export const suggestions = (state: State) => state.runs.suggestions

export const selectedRoles = (state: State) => {
   return state.runs.selectedRoles;
}
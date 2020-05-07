import {Map} from 'immutable';

export const isLoading = state => state.runs.loading

export const all = state => {
    if (!state.runs.byId) {
        return false
    }
    let list = [...state.runs.byId.values()]
    list.sort((a,b)=>a.start - b.start);
    return list;
}
export const testRuns = (id) => state => {
    if (!state.runs.byTest) {
        return false
    }
    let list = [...state.runs.byTest.get(id,Map({})).values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id) => state =>{
    if (!state.runs.byId) {
        return false
    }
    return state.runs.byId.get(id) || false;
}
export const filter = state => {
   const filteredIds = state.runs.filteredIds
   if (filteredIds == null) {
      return all(state);
   }
   const byId = state.runs.byId
   if (!byId) {
      return false
   }
   let list = [...byId.filter((v, k) => filteredIds.includes(k)).values()]
   list.sort((a,b)=>a.id - b.id);
   return list;
}

export const isFetchingSuggestions = state => {
   let suggestQuery = state.runs.suggestQuery
   return suggestQuery.length > 0;
}
export const suggestQuery = state => {
   let suggestQuery = state.runs.suggestQuery
   // Actually when this is called the suggestQuery.length should be <= 1
   return suggestQuery.length === 0 ? null : suggestQuery[suggestQuery.length - 1]
}

export const suggestions = state => state.runs.suggestions

export const selectedRoles = state => {
   return state.runs.selectedRoles;
}
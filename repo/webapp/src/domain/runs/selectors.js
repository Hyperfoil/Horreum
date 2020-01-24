import store from '../../store';
import {Map} from 'immutable';

export const all = () =>{
    let list = [...store.getState().runs.byId.values()]
    list.sort((a,b)=>a.start - b.start);
    return list;
}
export const testRuns = (id) => ()=>{
    let list = [...store.getState().runs.byTest.get(id,Map({})).values()]
    list.sort((a,b)=>a.id - b.id);
    return list;    
}
export const get = (id) => ()=>{
    return store.getState().runs.byId.get(id) || false;
}
export const filter = () => {
   const filteredIds = store.getState().runs.filteredIds
   if (filteredIds == null) {
      return all();
   }
   const byId = store.getState().runs.byId
   let list = [...byId.filter((v, k) => filteredIds.includes(k)).values()]
   list.sort((a,b)=>a.id - b.id);
   return list;
}

export const isFetchingSuggestions = () => {
   let suggestQuery = store.getState().runs.suggestQuery
   return suggestQuery.length > 0;
}
export const suggestQuery = () => {
   let suggestQuery = store.getState().runs.suggestQuery
   // Actually when this is called the suggestQuery.length should be <= 1
   return suggestQuery.length == 0 ? null : suggestQuery[suggestQuery.length - 1]
}

export const suggestions = () => store.getState().runs.suggestions

export const selectedRoles = () => {
   return store.getState().runs.selectedRoles;
}
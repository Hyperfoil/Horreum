import { State } from '../../store'
import { PaginationInfo } from '../../utils'

export const isLoading = (state: State) => state.runs.loading

function compare(a: any, b: any, desc: boolean): number {
    if (a === null) {
        return -1;
    } else if (b === null) {
        return 1;
    } else if (typeof a === "number" && typeof b === "number") {
        return (Number(a) - Number(b)) * (desc ? -1 : 1)
    } else {
        const an = Number(a)
        const bn = Number(b)
        if (isNaN(an) || isNaN(bn)) {
            return String(a).localeCompare(String(b)) * (desc ? -1 : 1)
        } else {
            return (an - bn) * (desc ? -1 : 1)
        }
    }
}

function sort(list: any[], pagination: PaginationInfo) {
    const desc = pagination.direction === "Descending"
    if (pagination.sort.startsWith("view_data:")) {
        const index = pagination.sort.substring(10, pagination.sort.indexOf(":", 10))
        return list.sort((a, b) => compare(a.view[index], b.view[index], desc))
    } else {
        return list.sort((a, b) => compare(a[pagination.sort], b[pagination.sort], desc))
    }
}

export const testRuns = (id: number, pagination: PaginationInfo, trashed: boolean) => (state: State) => {
    if (!state.runs.byTest) {
        return false
    }
    const testRuns = state.runs.byTest.get(id)
    if (!testRuns) {
        return []
    }
    let list = [...testRuns.values()].filter(run => state.runs.currentPage.includes(run.id))
    return sort(list, pagination);
}

export const get = (id: number) => (state: State) =>{
    if (!state.runs.byId) {
        return false
    }
    return state.runs.byId.get(id) || false;
}

export const filter = (pagination: PaginationInfo) => (state: State) => {
    const byId = state.runs.byId
    if (!byId) {
        return false
    }
    return sort([ ...byId.filter(run => state.runs.currentPage.includes(run.id)).values() ], pagination)
}

export const count = (state: State) => {
    return state.runs.currentTotal;
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
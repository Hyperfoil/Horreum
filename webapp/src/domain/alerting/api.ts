import { fetchApi } from '../../services/api';
import { Change, Variable } from './types'

const base = "/api/alerting"
const endPoints = {
    base: ()=>`${base}`,
    variables: (testId: number) => `${base}/variables?test=${testId}`,
    dashboard: (testId: number) => `${base}/dashboard?test=${testId}`,
    changes: (varId: number) => `${base}/changes?var=${varId}`,
    change: (changeId: number) => `${base}/change/${changeId}`,
    recalculate: (testId: number) => `${base}/recalculate?test=${testId}`
}

export const fetchVariables = (testId: number) => {
    return fetchApi(endPoints.variables(testId), null, 'get')
}

export const updateVariables = (testId: number, variables: Variable[]) => {
    return fetchApi(endPoints.variables(testId), variables, 'post', {}, 'response')
}

export const fetchDashboard = (testId: number) => {
    return fetchApi(endPoints.dashboard(testId), null, 'get')
}

export const fetchChanges = (varId: number) => {
    return fetchApi(endPoints.changes(varId), null, 'get')
}

export const updateChange = (change: Change) => {
    return fetchApi(endPoints.change(change.id), change, 'post', {}, 'response')
}

export const deleteChange = (changeId: number) => {
    return fetchApi(endPoints.change(changeId), null, 'delete', {}, 'response')
}

export const recalculate = (testId: number) => {
    return fetchApi(endPoints.recalculate(testId), null, 'post', {}, 'response')
}

export const recalculateProgress = (testId: number) => {
    return fetchApi(endPoints.recalculate(testId), null, 'get')
}
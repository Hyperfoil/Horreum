import { fetchApi } from '../../services/api';
import { Variable } from './types'

const base = "/api/alerting"
const endPoints = {
    base: ()=>`${base}`,
    variables: (testId: number)=>`${base}/variables?test=${testId}`,
}

export const variables = (testId: number) => {
    return fetchApi(endPoints.variables(testId), null, 'get')
}

export const updateVariables = (testId: number, variables: Variable[]) => {
    return fetchApi(endPoints.variables(testId), variables, 'post', {}, 'response')
}
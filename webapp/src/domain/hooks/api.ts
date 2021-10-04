import { fetchApi } from "../../services/api"
import { Hook } from "./reducers"

const base = "/api/hook"
const endPoints = {
    base: () => `${base}`,
    crud: (id: number) => `${base}/${id}/`,
    list: () => `${base}/list/`,
    testHooks: (id: number) => `${base}/test/${id}`,
    prefixes: () => `${base}/prefixes`,
    prefix: (id: number) => `${base}/prefixes/${id}`,
}

export const allHooks = () => {
    return fetchApi(endPoints.list(), null, "get")
}
export const addHook = (payload: Hook) => {
    return fetchApi(endPoints.base(), payload, "post")
}
export const getHook = (id: number) => {
    return fetchApi(endPoints.crud(id), null, "get")
}
export const removeHook = (id: number) => {
    return fetchApi(endPoints.crud(id), null, "delete")
}

export const fetchHooks = (testId: number) => {
    return fetchApi(endPoints.testHooks(testId), null, "get")
}

export function fetchPrefixes() {
    return fetchApi(endPoints.prefixes(), null, "get")
}

export function addPrefix(prefix: string) {
    return fetchApi(endPoints.prefixes(), prefix, "post", { "Content-Type": "text/plain" })
}

export function removePrefix(id: number) {
    return fetchApi(endPoints.prefix(id), null, "delete")
}

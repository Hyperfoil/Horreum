import { fetchApi } from "../../services/api"
import { Test, View } from "./reducers"
import { Hook } from "../hooks/reducers"

const base = "/api/test"
const endPoints = {
    base: () => `${base}`,
    crud: (id: number) => `${base}/${id}`,
    schema: (id: number) => `${base}/${id}/schema`,
    view: (id: number) => `${base}/${id}/view`,
    hook: (id: number) => `${base}/${id}/hook`,
    summary: (roles?: string, folder?: string) =>
        `${base}/summary?` +
        [roles ? "roles=" + roles : undefined, folder ? "folder=" + encodeURIComponent(folder) : undefined]
            .filter(x => !!x)
            .join("&"),
    folders: (roles?: string) => `${base}/folders` + (roles ? "?roles=" + roles : ""),
    move: (id: number, folder: string) => `${base}/${id}/move?folder=${encodeURIComponent(folder)}`,
    tokens: (id: number) => `${base}/${id}/tokens`,
    addToken: (id: number) => `${base}/${id}/addToken`,
    revokeToken: (testId: number, tokenId: number) => `${base}/${testId}/revokeToken/${tokenId}`,
    updateAccess: (id: number, owner: string, access: string) =>
        `${base}/${id}/updateAccess?owner=${owner}&access=${access}`,
    tags: (testId: number, trashed: boolean) => `${base}/${testId}/tags?trashed=${trashed}`,
}

export const updateView = (testId: number, view: View) => {
    return fetchApi(endPoints.view(testId), view, "post")
}
export const updateHook = (testId: number, hook: Hook) => {
    return fetchApi(endPoints.hook(testId), hook, "post")
}
export const get = (id: number) => {
    return fetchApi(endPoints.crud(id), null, "get")
}
export function summary(roles?: string, folder?: string) {
    return fetchApi(endPoints.summary(roles, folder), null, "get")
}

export function folders(roles?: string) {
    return fetchApi(endPoints.folders(roles), null, "get")
}

export const send = (test: Test) => {
    return fetchApi(endPoints.base(), test, "post")
}

export const tokens = (id: number) => fetchApi(endPoints.tokens(id), null, "get")

export const addToken = (id: number, value: string, description: string, permissions: number) =>
    fetchApi(
        endPoints.addToken(id),
        {
            value,
            description,
            permissions,
        },
        "post",
        {
            accept: "text/plain",
        },
        "text"
    )

export const revokeToken = (testId: number, tokenId: number) =>
    fetchApi(endPoints.revokeToken(testId, tokenId), null, "post", {}, "response")

export const updateAccess = (id: number, owner: string, access: string) => {
    // TODO: fetchival does not support form parameters, it tries to JSONify everything
    return fetchApi(endPoints.updateAccess(id, owner, access), null, "post", {}, "response")
    //                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
    //                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

export function updateFolder(testId: number, folder: string) {
    return fetchApi(endPoints.move(testId, folder), null, "post")
}

export const deleteTest = (id: number) => fetchApi(endPoints.crud(id), null, "delete")

export const fetchTags = (testId: number, trashed: boolean) => {
    return fetchApi(endPoints.tags(testId, trashed), null, "get")
}

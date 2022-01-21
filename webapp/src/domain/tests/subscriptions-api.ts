import { fetchApi } from "../../services/api"

export type Watch = {
    id?: number
    testId: number
    users: string[]
    optout: string[]
    teams: string[]
}

const base = "/api/subscriptions"
export function getSubscription(testId: number) {
    return fetchApi(`${base}/${testId}`, null, "get")
}

export function updateSubscription(watch: Watch) {
    return fetchApi(`${base}/${watch.testId}`, watch, "post")
}

export function all(folder?: string) {
    return fetchApi(`${base}/${folder ? "?folder=" + folder : ""}`, null, "get")
}

export function addUserOrTeam(testId: number, userOrTeam: string) {
    return fetchApi(`${base}/${testId}/add`, userOrTeam, "post")
}

export function removeUserOrTeam(testId: number, userOrTeam: string) {
    return fetchApi(`${base}/${testId}/remove`, userOrTeam, "post")
}

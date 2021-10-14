import { fetchApi } from "../../services/api"

const base = "/api/user"

export type User = {
    id: string
    username: string
    firstName?: string
    lastName?: string
    email?: string
}

export function search(query: string) {
    return fetchApi(`${base}/search?query=${encodeURIComponent(query)}`, null, "get")
}

export function info(usernames: string[]) {
    return fetchApi(`${base}/info`, usernames, "post")
}

export function teams() {
    return fetchApi(`${base}/teams`, null, "get")
}

export function teamMembers(team: string) {
    return fetchApi(`${base}/team/${team}/members`)
}

export function updateTeamMembers(team: string, roles: Map<string, string[]>) {
    return fetchApi(`${base}/team/${team}/members`, Object.fromEntries(roles), "post")
}

export function createUser(user: User, password: string, team: string, roles: string[]) {
    return fetchApi(`${base}/createUser`, { user, password, team, roles }, "post")
}

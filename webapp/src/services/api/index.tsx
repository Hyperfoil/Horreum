import fetchival from "fetchival"
import store from "../../store"
import { ADD_ALERT } from "../../alerts"
import { TryLoginAgain } from "../../auth"

const serialize = (input: any): any => {
    if (input === null || input === undefined) {
        return input
    } else if (Array.isArray(input)) {
        return input.map(v => serialize(v))
    } else if (typeof input === "function") {
        return input.toString()
    } else if (typeof input === "object") {
        const rtrn: { [key: string]: any } = {}
        Object.keys(input).forEach(key => {
            rtrn[key] = serialize(input[key])
        })
        return rtrn
    } else {
        return input
    }
}

const deserialize = (input: any): any => {
    if (input === null || input === undefined) {
        return input
    } else if (Array.isArray(input)) {
        return input.map(v => deserialize(v))
    } else if (typeof input === "object") {
        const rtrn: { [key: string]: any } = {}
        Object.keys(input).forEach(key => {
            rtrn[key] = deserialize(input[key])
        })
        return rtrn
    } else if (typeof input === "string") {
        if (input.includes("=>") || input.startsWith("function ")) {
            try {
                // eslint-disable-next-line
                return new Function("return " + input)()
            } catch (e) {
                console.warn("Error deserializing " + input)
                console.warn(e)
                return input
            }
        } else {
            return input
        }
    } else {
        return input
    }
}

export function fetchApi(endPoint: string, payload: any = {}, method = "get", headers = {}, responseAs = "json") {
    //const accessToken = sessionSelectors.get().tokens.access.value;
    const serialized = serialize(payload)
    const keycloak = store.getState().auth.keycloak
    let updateTokenPromise
    if (keycloak != null && keycloak.authenticated) {
        updateTokenPromise = keycloak.updateToken(30)
    } else {
        updateTokenPromise = Promise.resolve(false)
    }
    return updateTokenPromise
        .then(() => {
            const authHeaders = Object.create(null)
            if (keycloak != null && keycloak.token != null) {
                authHeaders.Authorization = "Bearer " + keycloak.token
            }
            return fetchival(endPoint, {
                headers: { ...authHeaders, ...headers },
                responseAs: responseAs,
            })[method.toLowerCase()](serialized)
        })
        .then(
            response => {
                return Promise.resolve(deserialize(response))
            },
            e => {
                if (e === true) {
                    // This happens when token update fails
                    store.dispatch({
                        type: ADD_ALERT,
                        alert: {
                            type: "TOKEN_UPDATE_FAILED",
                            title: "Token update failed",
                            content: <TryLoginAgain />,
                        },
                    })
                    return Promise.reject()
                } else if (e.response) {
                    if (e.response.status === 401 || e.response.status === 403) {
                        store.dispatch({
                            type: ADD_ALERT,
                            alert: {
                                type: "REQUEST_FORBIDDEN",
                                title: "Request failed due to insufficient permissions",
                                content: <TryLoginAgain />,
                            },
                        })
                    }
                    const contentType = e.response.headers.get("content-type")
                    if (contentType === "application/json") {
                        return e.response.json().then((body: any) => Promise.reject(body))
                    } else {
                        return e.response.text().then((text: any) => Promise.reject(text))
                    }
                } else {
                    return Promise.reject(e)
                }
            }
        )
}

import { DefaultApi } from "./generated/apis"
import { Configuration, Middleware } from "./generated"
import store from "./store"
import { ADD_ALERT } from "./alerts"
import { TryLoginAgain } from "./auth"
export * from "./generated/models"

const authMiddleware: Middleware = {
    pre: ctx => {
        const keycloak = store.getState().auth.keycloak
        if (keycloak != null && keycloak.authenticated) {
            return keycloak.updateToken(30).then(
                () => {
                    if (keycloak != null && keycloak.token != null) {
                        return {
                            url: ctx.url,
                            init: {
                                ...ctx.init,
                                headers: { ...ctx.init.headers, Authorization: "Bearer " + keycloak.token },
                            },
                        }
                    }
                },
                e => {
                    store.dispatch({
                        type: ADD_ALERT,
                        alert: {
                            type: "TOKEN_UPDATE_FAILED",
                            title: "Token update failed",
                            content: <TryLoginAgain />,
                        },
                    })
                    return Promise.reject(e)
                }
            )
        } else {
            return Promise.resolve()
        }
    },
    post: ctx => {
        if (ctx.response.ok) {
            return Promise.resolve(ctx.response)
        } else if (ctx.response.status === 401 || ctx.response.status === 403) {
            store.dispatch({
                type: ADD_ALERT,
                alert: {
                    type: "REQUEST_FORBIDDEN",
                    title: "Request failed due to insufficient permissions",
                    content: <TryLoginAgain />,
                },
            })

            const contentType = ctx.response.headers.get("content-type")
            if (contentType === "application/json") {
                return ctx.response.json().then((body: any) => Promise.reject(body))
            } else {
                return ctx.response.text().then((text: any) => Promise.reject(text))
            }
        } else {
            // We won't reject it because that would skip other middleware
            return Promise.resolve(ctx.response)
        }
    },
}

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

const serializationMiddleware: Middleware = {
    pre: ctx => {
        return Promise.resolve({ url: ctx.url, init: { ...ctx.init, body: serialize(ctx.init.body) } })
    },
    // we won't deserialize functions eagerly
}

const noResponseMiddleware: Middleware = {
    post: ctx => {
        if (ctx.response.status === 204) {
            const rsp = ctx.response.clone()
            rsp.json = () => Promise.resolve(undefined)
            return Promise.resolve(rsp)
        } else if (ctx.response.status >= 400) {
            return ctx.response.text().then(err => {
                if (err) {
                    return Promise.reject(err)
                } else {
                    return Promise.reject(ctx.response.status + " " + ctx.response.statusText)
                }
            })
        }
        return Promise.resolve(ctx.response)
    },
}

const Api = new DefaultApi(
    new Configuration({
        basePath: window.location.origin,
        middleware: [authMiddleware, serializationMiddleware, noResponseMiddleware],
    })
)

export default Api

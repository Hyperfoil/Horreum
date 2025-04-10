import {
    ActionApi,
    AlertingApi,
    BannerApi,
    ChangesApi,
    ConfigApi,
    DatasetApi,
    ExperimentApi,
    LogApi,
    NotificationsApi,
    ReportApi,
    RunApi,
    SchemaApi,
    SqlApi,
    SubscriptionsApi,
    TestApi,
    UiApi,
    UserApi,

} from "./generated/apis"
import {
    Access,
    Action, AllowedSite,
    Configuration,
    Middleware, RunSummary,
    Schema, type SortDirection,
    Test,
    TestListing, TestSummary, Transformer, View, Watch,
} from "./generated"
import store from "./store"
import {AlertContextType} from "./context/@types/appContextTypes";
export * from "./generated/models"

const authMiddleware: Middleware = {
    pre: ctx => {
        const keycloak = store.getState().auth.keycloak
        const basicAuthToken = store.getState().auth.basicAuthToken
        if (keycloak != null && keycloak.authenticated) {
            return keycloak.updateToken(30).then(
                () => {
                    if (keycloak != null && keycloak.token != null) {
                        return {
                            url: ctx.url,
                            init: {
                                ...ctx.init,
                                headers: {...ctx.init.headers, Authorization: "Bearer " + keycloak.token},
                            },
                        }
                    }
                },
            /* e => {
                    store.dispatch({
                        type: ADD_ALERT,
                        alert: {
                            type: "TOKEN_UPDATE_FAILED",
                            title: "Token update failed",
                            content: <TryLoginAgain/>,
                        },
                    })
                    return Promise.reject(e)
                } */
            )
        } else if (basicAuthToken) {
            return Promise.resolve({
                url: ctx.url,
                init: {
                    ...ctx.init,
                    credentials: "omit", // this prevents the browser from showing the native auth dialog
                    headers: {...ctx.init.headers, Authorization: "Basic " + basicAuthToken},
                },
            })
        } else {
            return Promise.resolve()
        }
    },
    post: ctx => {
        if (ctx.response.ok) {
            return Promise.resolve(ctx.response)
        } else if (ctx.response.status === 401 || ctx.response.status === 403) {
/*
            store.dispatch({
                type: ADD_ALERT,
                alert: {
                    type: "REQUEST_FORBIDDEN",
                    title: "Request failed due to insufficient permissions",
                    content: <TryLoginAgain/>,
                },
            })
*/
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
        return Promise.resolve({url: ctx.url, init: {...ctx.init, body: serialize(ctx.init.body)}})
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


const configuration = new Configuration({
    basePath: window.location.origin,
    middleware: [authMiddleware, serializationMiddleware, noResponseMiddleware],
});

const actionApi = new ActionApi(configuration)
export const alertingApi = new AlertingApi(configuration)
export const bannerApi = new BannerApi(configuration)
export const changesApi = new ChangesApi(configuration)
export const datasetApi = new DatasetApi(configuration)
export const experimentApi = new ExperimentApi(configuration)
export const logApi = new LogApi(configuration)
export const notificationsApi = new NotificationsApi(configuration)
export const reportApi = new ReportApi(configuration)
export const runApi = new RunApi(configuration)
export const schemaApi = new SchemaApi(configuration)
export const sqlApi = new SqlApi(configuration)
export const subscriptionsApi = new SubscriptionsApi(configuration)
export const testApi = new TestApi(configuration)
export const uiApi = new UiApi(configuration)
export const userApi = new UserApi(configuration)
export const configApi = new ConfigApi(configuration)


export interface TestStorage extends Test {
    datasets?: number // dataset count in AllTests
    runs?: number // run count in AllTests
    watching?: string[]
    views?: View[]
}

//Actions
export function addAction(action: Action, alerting: AlertContextType): Promise<Action> {
    return apiCall(actionApi.addAction(action), alerting, "ADD_ACTION", "Failed to add action");
}
export function addGlobalAction(action: Action, alerting: AlertContextType): Promise<Action> {
    return apiCall(actionApi.addGlobalAction(action), alerting, "ADD_ACTION", "Failed to add action");
}
export function addSite(prefix: string, alerting: AlertContextType): Promise<AllowedSite> {
    return apiCall(actionApi.addSite(prefix), alerting, "ADD_ALLOWED_SITE", "Failed to add allowed site");
}
export function deleteSite(id: number, alerting: AlertContextType): Promise<void> {
    return apiCall(actionApi.deleteSite(id), alerting, "REMOVE_ALLOWED_SITE", "Failed to remove allowed site");

}
export function getTestActions(testId: number, alerting: AlertContextType): Promise<Action[]> {
    return apiCall(actionApi.getTestActions(testId), alerting, "GET_TEST_ACTIONS", "Failed to get test actions");
}

export function getAllowedSites(alerting: AlertContextType): Promise<AllowedSite[]> {
    return apiCall(actionApi.allowedSites(), alerting, "GET_ALLOWED_SITES", "Failed to get allowed sites");
}

export function allActions(alerting: AlertContextType): Promise<Action[]> {
    return apiCall(actionApi.listActions(), alerting, "GET_ACTIONS", "Failed to get actions");
}

export function deleteGlobalAction(id: number, alerting: AlertContextType): Promise<void> {
    return apiCall(actionApi.deleteGlobalAction(id), alerting, "REMOVE_ACTION", "Failed to remove global action");
}

export function updateAction(action: Action, alerting: AlertContextType): Promise<Action> {
    return apiCall(actionApi.updateAction(action), alerting, "UPDATE_ACTION", "Failed to update action");
}

//Schemas
export function getSchema(schemaId: number, alerting: AlertContextType): Promise<Schema> {
    return apiCall(schemaApi.getSchema(schemaId), alerting, "GET_SCHEMA", "Failed to fetch schema");
}

//Tests
export function addUserOrTeam(id: number, userOrTeam: string, alerting: AlertContextType) : Promise<string[]> {
    return apiCall(subscriptionsApi.addUserOrTeam(id, userOrTeam), alerting, "ADD_SUBSCRIPTION", "Failed to add test subscriptions");
}

export function deleteTest(id: number, alerting: AlertContextType) : Promise<void>{
    return apiCall(testApi.deleteTest(id), alerting, "DELETE_TEST", "Failed to delete test " + id);
}
export function fetchTestsSummariesByFolder(alertingContext: AlertContextType, direction?: SortDirection, folder?: string, limit?: number, page?: number, roles?: string, name?: string): Promise<TestListing> {
    return apiCall(testApi.getTestSummary(roles, folder, limit, page, direction, name), alertingContext, "FETCH_TEST_SUMMARY", "Failed to fetch test summary.");
}
export function fetchFolders(alerting: AlertContextType): Promise<string[]> {
    return apiCall(testApi.folders(), alerting, "FETCH_FOLDERS", "Failed to fetch folders.");
}

export function fetchTestsSummary(alertingContext: AlertContextType, roles?: string, folder?: string,
                                  limit?: number, page?: number, direction?: SortDirection) : Promise<TestListing> {
    return apiCall(testApi.getTestSummary(roles, folder, limit, page, direction), alertingContext, "FETCH_TEST_SUMMARY", "Failed to fetch test summary.");
}

export function fetchTests(alertingContext: AlertContextType,roles?: string, folder?: string) : Promise<Test[]> {
    return apiCall(testApi.getTestSummary( roles, folder, -1, -1, undefined), alertingContext, "FETCH_TEST_SUMMARY", "Failed to fetch test summary.")
        .then(summary => summary.tests?.map(t => mapTestSummaryToTest(t)) || [])
}


export function fetchTest(id: number, alerting: AlertContextType): Promise<Test> {
    return apiCall(testApi.getTest(id), alerting, "FETCH_TEST", "Failed to fetch test; the test may not exist or you don't have sufficient permissions to access it.");
}
export function removeUserOrTeam(id: number, userOrTeam: string, alerting: AlertContextType) {
    return apiCall(subscriptionsApi.removeUserOrTeam(id, userOrTeam), alerting, "REMOVE_SUBSCRIPTION", "Failed to remove test subscriptions");
}

export function addTest(test: Test, alerting: AlertContextType): Promise<Test> {
    return apiCall(testApi.addTest(test), alerting, "SEND_TEST", "Failed to send test");
}

export function updateTest(test: Test, alerting: AlertContextType): Promise<Test> {
    return apiCall(testApi.updateTest(test), alerting, "UPDATE_TEST", "Failed to update test");
}


export function fetchViews(testId: number, alerting: AlertContextType): Promise<View[]> {
    return apiCall(uiApi.getViews(testId), alerting, "FETCH_VIEWS", "Failed to fetch test views; the views may not exist or you don't have sufficient permissions to access them.");
}

export function updateAccess(id: number, owner: string, access: Access, alerting: AlertContextType) : Promise<void> {
    return apiCall(testApi.updateTestAccess(id, owner, access), alerting, "UPDATE_ACCESS", "Failed to update test access");
}
export function updateView(alerting: AlertContextType, testId: number, view: View): Promise<View> {
    for (const c of view.components) {
        if (c.labels.length === 0) {
            alerting.dispatchError(
                undefined,
                "VIEW_UPDATE",
                "Column " + c.headerName + " is invalid; must set at least one label."
            )
            return Promise.reject()
        }
    }
    view.testId = testId
    return apiCall(uiApi.updateView(view), alerting, "VIEW_UPDATE", "View update failed.");
}

export function deleteView(alerting: AlertContextType, testId: number, viewId: number): Promise<void> {
    return apiCall(uiApi.deleteView(testId, viewId), alerting, "VIEW_DELETE", "View update failed.");
}

export function updateFolder(testId: number, prevFolder: string, newFolder: string, alerting: AlertContextType): Promise<void> {
    return apiCall(testApi.updateFolder(testId, newFolder), alerting, "TEST_FOLDER_UPDATE", "Cannot update test folder");
}

export function updateOrCreateActions(testId: number, actions: Action[], alerting: AlertContextType) {
    const promises: any[] = []
    actions.forEach(action => {
        promises.push(
            (action.testId = testId),
            action.id > 0 ? updateAction(action, alerting) : addAction(action, alerting)
        )
    })
    return Promise.all(promises)
}


/*
function watchToList(watch: Watch) {
    return [...watch.users, ...watch.teams, ...watch.optout.map((u: string) => `!${u}`)]
}
*/

export function getSubscription(testId: number, alerting: AlertContextType) : Promise<Watch> {
    return apiCall(subscriptionsApi.getSubscription(testId), alerting, "SUBSCRIPTION_LOOKUP", "Subscription lookup failed");
}

export function updateSubscription(watch: Watch, alerting: AlertContextType) : Promise<void> {
    return apiCall(subscriptionsApi.updateSubscription(watch.testId, watch), alerting, "SUBSCRIPTION_UPDATE", "Failed to update subscription");
}




export function updateTransformers(testId: number, transformers: Transformer[], alerting: AlertContextType) : Promise<void> {
    return apiCall(testApi.updateTransformers(testId, transformers.map(t => t.id)), alerting, "UPDATE_TRANSFORMERS", "Failed to update transformers for test " + testId);
}

export function updateChangeDetection(
    alerting: AlertContextType,
    testId: number,
    timelineLabels: string[] | undefined,
    timelineFunction: string | undefined,
    fingerprintLabels: string[],
    fingerprintFilter: string | undefined
) {
    const update = {
        timelineLabels,
        timelineFunction,
        fingerprintLabels,
        fingerprintFilter,
    }
    return apiCall(alertingApi.updateChangeDetection(testId, update), alerting, "UPDATE_CHANGE_DETECTION", "Failed to update change detection for test " + testId);

}

///Runs
export function fetchRunSummary(id: number, alerting: AlertContextType): Promise<RunSummary> {
    return apiCall(runApi.getRunSummary(id), alerting, "FETCH_RUN_SUMMARY", "Failed to fetch data for run " + id + ", try uploading a Run and use new Run id instead.");

}

export function recalculateDatasets(id: number, testid: number, alerting: AlertContextType) : Promise<number[]> {
    return apiCall(runApi.recalculateRunDatasets(id), alerting, "RECALCULATE_DATASETS", "Failed to recalculate datasets");
}

export function trash(alerting: AlertContextType, id: number, isTrashed = true) : Promise<void> {
    return apiCall(runApi.trash(id, isTrashed), alerting, "RUN_TRASH", "Failed to trash run ID " + id);
}

export function updateRunAccess (id: number, testid: number, owner: string, access: Access, alerting: AlertContextType) : Promise<void> {
    return apiCall(runApi.updateRunAccess(id, owner, access), alerting, "UPDATE_RUN_ACCESS", "Failed to update run access");
}
export function updateDescription(id: number, testid: number, description: string, alerting: AlertContextType) : Promise<void> {
    return apiCall(runApi.updateDescription(id, description), alerting, "RUN_UPDATE", "Failed to update description for run ID " + id);
}


//Utils
export function apiCall<T>(apiCall: Promise<T>, alerting: AlertContextType, errorKey: string, errorMessage: string): Promise<T> {
    return apiCall.then(
        response => response,
        error => alerting.dispatchError(error, errorKey, errorMessage)
    )
}



export function mapTestSummaryToTest(testSummary: TestSummary): Test {
    return {...testSummary, notificationsEnabled: false }
}

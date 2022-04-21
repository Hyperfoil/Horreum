import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import { Access } from "../../auth"
import { ThunkDispatch } from "redux-thunk"
import { Hook } from "../hooks/reducers"
import { Run } from "../runs/reducers"
import { Transformer } from "../schemas/api"
import { AddAlertAction } from "../../alerts"

export interface Token {
    id: number
    description: string
    permissions: number
}

export type RenderFunction = (value: any, row: any, token: string | undefined) => any

export interface ViewComponent {
    id: number
    headerName: string
    labels: string[]
    render: string | RenderFunction | undefined
    headerOrder: number
}

export interface View {
    name: string
    components: ViewComponent[]
}

export type CompareFunction = (runs: Run[]) => string

export interface Test {
    id: number
    name: string
    folder?: string
    description: string
    compareUrl: string | CompareFunction | undefined
    tags: string
    tagsCalculation?: string
    fingerprintLabels: string[] | null
    fingerprintFilter: string | null
    owner: string
    access: Access
    tokens: Token[]
    defaultView?: View
    datasets?: number // dataset count in AllTests
    runs?: number // run count in AllTests
    watching?: string[]
    notificationsEnabled: boolean
    transformers: Transformer[]
}

export class TestsState {
    byId?: Map<number, Test> = undefined
    loading = false
    allFolders: string[] = []
    // we need to store watches independently as the information
    // can arrive before the actual test list
    watches: Map<number, string[] | undefined> = Map<number, string[] | undefined>()
}

export interface LoadingAction {
    type: typeof actionTypes.LOADING
    isLoading: boolean
}

export interface LoadedSummaryAction {
    type: typeof actionTypes.LOADED_SUMMARY
    tests: Test[]
}

export interface LoadedTestAction {
    type: typeof actionTypes.LOADED_TEST
    test: Test
}

export interface DeleteAction {
    type: typeof actionTypes.DELETE
    id: number
}

export interface UpdateAccessAction {
    type: typeof actionTypes.UPDATE_ACCESS
    id: number
    owner: string
    access: Access
}

export interface UpdateTestWatchAction {
    type: typeof actionTypes.UPDATE_TEST_WATCH
    byId: Map<number, string[] | undefined>
}

export interface UpdateViewAction {
    type: typeof actionTypes.UPDATE_VIEW
    testId: number
    view: View
}

export interface UpdateHookAction {
    type: typeof actionTypes.UPDATE_HOOK
    testId: number
    hook: Hook
}

export interface UpdateTokensAction {
    type: typeof actionTypes.UPDATE_TOKENS
    testId: number
    tokens: Token[]
}

export interface RevokeTokenAction {
    type: typeof actionTypes.REVOKE_TOKEN
    testId: number
    tokenId: number
}

export interface UpdateFoldersAction {
    type: typeof actionTypes.UPDATE_FOLDERS
    folders: string[]
}

export interface UpdateFolderAction {
    type: typeof actionTypes.UPDATE_FOLDER
    testId: number
    prevFolder: string
    newFolder: string
}

export interface UpdateTransformersAction {
    type: typeof actionTypes.UPDATE_TRANSFORMERS
    testId: number
    transformers: Transformer[]
}

export interface UpdateFingerprintAction {
    type: typeof actionTypes.UPDATE_FINGERPRINT
    testId: number
    labels: string[]
    filter: string | null
}

export type TestAction =
    | LoadingAction
    | LoadedSummaryAction
    | LoadedTestAction
    | DeleteAction
    | UpdateAccessAction
    | UpdateTestWatchAction
    | UpdateViewAction
    | UpdateHookAction
    | UpdateTokensAction
    | RevokeTokenAction
    | UpdateFoldersAction
    | UpdateFolderAction
    | UpdateTransformersAction
    | UpdateFingerprintAction

export type TestDispatch = ThunkDispatch<any, unknown, TestAction | AddAlertAction>

export const reducer = (state = new TestsState(), action: TestAction) => {
    switch (action.type) {
        case actionTypes.LOADING:
            state.loading = action.isLoading
            break
        case actionTypes.LOADED_SUMMARY:
            {
                state.loading = false
                let byId = Map<number, Test>()
                action.tests.forEach(test => {
                    byId = byId.set(test.id, test)
                })
                state.byId = byId
            }
            break
        case actionTypes.LOADED_TEST:
            state.loading = false
            if (!state.byId) {
                state.byId = Map<number, Test>()
            }
            state.byId = (state.byId as Map<number, Test>).set(action.test.id, action.test)
            break
        case actionTypes.UPDATE_ACCESS:
            {
                const test = state.byId?.get(action.id)
                if (test) {
                    state.byId = state.byId?.set(action.id, { ...test, owner: action.owner, access: action.access })
                }
            }
            break
        case actionTypes.DELETE:
            {
                state.byId = state.byId?.delete(action.id)
            }
            break
        case actionTypes.UPDATE_TEST_WATCH:
            {
                state.watches = state.watches.merge(action.byId)
            }
            break
        case actionTypes.UPDATE_VIEW:
            {
                const test = state.byId?.get(action.testId)
                if (test) {
                    state.byId = state.byId?.set(action.testId, { ...test, defaultView: action.view })
                }
            }
            break
        case actionTypes.UPDATE_TOKENS:
            {
                const test = state.byId?.get(action.testId)
                if (test) {
                    state.byId = state.byId?.set(action.testId, { ...test, tokens: action.tokens })
                }
            }
            break
        case actionTypes.REVOKE_TOKEN:
            {
                const test = state.byId?.get(action.testId)
                if (test) {
                    state.byId = state.byId?.set(action.testId, {
                        ...test,
                        tokens: test.tokens.filter(t => t.id !== action.tokenId),
                    })
                }
            }
            break
        case actionTypes.UPDATE_HOOK:
            {
                //TODO: define state changes
            }
            break
        case actionTypes.UPDATE_FOLDERS:
            {
                state.allFolders = action.folders
            }
            break
        case actionTypes.UPDATE_FOLDER: {
            // the byId has only the entries from current page, and if we're moving the
            // test elsewhere we're effectively removing it from the current view
            state.byId = state.byId?.remove(action.testId)
            break
        }
        case actionTypes.UPDATE_TRANSFORMERS: {
            const test = state.byId?.get(action.testId)
            if (test) {
                state.byId = state.byId?.set(action.testId, { ...test, transformers: action.transformers })
            }
            break
        }
        case actionTypes.UPDATE_FINGERPRINT: {
            const test = state.byId?.get(action.testId)
            if (test) {
                state.byId = state.byId?.set(action.testId, {
                    ...test,
                    fingerprintLabels: action.labels,
                    fingerprintFilter: action.filter,
                })
            }
            break
        }
        default:
    }
    return state
}

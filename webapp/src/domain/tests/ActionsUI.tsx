import { useEffect, useState } from "react"
import { useDispatch } from "react-redux"
import {
    Alert,
    AlertVariant,
    Button,
    DataList,
    DataListAction,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    Title,
} from "@patternfly/react-core"

import { useTester } from "../../auth"
import { alertAction } from "../../alerts"
import { noop } from "../../utils"
import { TestDispatch } from "./reducers"
import Api, { Action } from "../../api"
import { TabFunctionsRef } from "../../components/SavedTabs"
import { updateActions } from "./actions"
import { testEventTypes } from "../actions/reducers"
import ActionComponentForm from "../actions/ActionComponentForm"
import ActionLogModal from "./ActionLogModal"
import { Navigate  } from "react-router-dom"

type ActionsProps = {
    testId: number
    testOwner?: string
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

export default function ActionsUI({ testId, testOwner, funcsRef, onModified }: ActionsProps) {
    const [actions, setActions] = useState<Action[]>([])
    const [logModalOpen, setLogModalOpen] = useState(false)
    const isTester = useTester(testOwner)
    const hasDuplicates = new Set(actions.map(h => h.event + "_" + h.config.url)).size !== actions.length

    const dispatch = useDispatch<TestDispatch>()
    useEffect(() => {
        if (!testId || !isTester) {
            return
        }
        Api.actionServiceGetTestActions(testId).then(
            response => setActions(response),
            error => dispatch(alertAction("ACTION_FETCH", "Failed to fetch actions", error))
        )
    }, [testId, isTester, dispatch])

    funcsRef.current = {
        save: () => dispatch(updateActions(testId, actions)).catch(noop),
        reset: () => {
            // Perform a deep copy of the view object to prevent modifying store
            setActions(JSON.parse(JSON.stringify(actions)) as Action[])
        },
    }

    if (!isTester) {
        return <Navigate to="/" />
    }
    return (
        <>
            {hasDuplicates && (
                <Alert variant={AlertVariant.warning} title="Some actions contain duplicate URLs">
                    Some webooks contain duplicate combinations of URLs and event types. Please correct these.
                </Alert>
            )}
            <div
                style={{
                    marginTop: "16px",
                    marginBottom: "16px",
                    width: "100%",
                    display: "flex",
                }}
            >
                <Title headingLevel="h3" style={{ flexGrow: 100 }}>
                    Actions
                </Title>
                <Button
                    onClick={() => {
                        const newAction: Action = {
                            id: -1,
                            event: testEventTypes[0][0],
                            type: "http",
                            config: { url: "" },
                            secrets: {},
                            testId,
                            active: true,
                            runAlways: false,
                        }
                        setActions([...actions, newAction])
                        onModified(true)
                    }}
                >
                    New Action
                </Button>
                <Button variant="secondary" onClick={() => setLogModalOpen(true)}>
                    Show log
                </Button>
                <ActionLogModal
                    testId={testId}
                    title={"Actions log"}
                    emptyMessage={"There are no log messages."}
                    isOpen={logModalOpen}
                    onClose={() => setLogModalOpen(false)}
                />
            </div>

            {(!actions || actions.length === 0) && "The are no Actions defined"}

            <DataList aria-label="List of actions">
                {actions
                    .filter(action => action.active)
                    .map((action, i) => (
                        <DataListItem key={i} aria-labelledby="">
                            <DataListItemRow>
                                <DataListItemCells
                                    dataListCells={[
                                        <DataListCell key="content">
                                            <ActionComponentForm
                                                style={{
                                                    gridGap: "2px",
                                                    width: "100%",
                                                    float: "left",
                                                    marginBottom: "25px",
                                                }}
                                                action={action}
                                                onUpdate={a => {
                                                    setActions(actions.map(a2 => (action === a2 ? a : a2)))
                                                    onModified(true)
                                                }}
                                                eventTypes={testEventTypes}
                                                isTester={isTester}
                                            />
                                        </DataListCell>,
                                    ]}
                                />
                                {isTester && (
                                    <DataListAction
                                        style={{
                                            flexDirection: "column",
                                            justifyContent: "center",
                                        }}
                                        id="delete"
                                        aria-labelledby="delete"
                                        aria-label="Settings actions"
                                        isPlainButtonAction
                                    >
                                        <Button
                                            style={{ width: "110%" }}
                                            variant="danger"
                                            onClick={() => {
                                                actions[i].active = false
                                                setActions([...actions])
                                                onModified(true)
                                            }}
                                        >
                                            Delete
                                        </Button>
                                    </DataListAction>
                                )}
                            </DataListItemRow>
                        </DataListItem>
                    ))}
            </DataList>
        </>
    )
}

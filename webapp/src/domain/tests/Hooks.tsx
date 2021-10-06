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
    Form,
    FormGroup,
    FormSelect,
    FormSelectOption,
    Title,
} from "@patternfly/react-core"

import { useTester } from "../../auth"
import { alertAction } from "../../alerts"
import { noop } from "../../utils"
import { TestDispatch } from "./reducers"
import { Hook } from "../hooks/reducers"
import { TabFunctionsRef } from "./Test"
import { updateHooks } from "./actions"
import * as api from "../hooks/api"
import { testHookEventTypes } from "../hooks/reducers"
import HookUrlSelector from "../../components/HookUrlSelector"

type HookComponentFormProps = {
    c: Hook
    onChange(): void
    isTester: boolean
}

const HookComponentForm = ({ c, onChange, isTester }: HookComponentFormProps) => {
    const [eventType, setEventType] = useState(c.type)

    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", float: "left", marginBottom: "25px" }}>
            <HookUrlSelector
                active={isTester}
                value={c.url || ""}
                setValue={value => {
                    c.url = value
                    onChange()
                }}
                isReadOnly={!isTester}
            />
            <FormGroup label="Event Type" fieldId="event-type">
                <FormSelect
                    id="type"
                    validated={"default"}
                    value={eventType}
                    onChange={value => {
                        c.type = value
                        setEventType(value)
                        onChange()
                    }}
                    aria-label="Event Type"
                >
                    {testHookEventTypes.map((option, index) => {
                        return <FormSelectOption key={index} value={option} label={option} />
                    })}
                </FormSelect>
            </FormGroup>
        </Form>
    )
}

type HooksProps = {
    testId: number
    // testWebHooks: TestWebHooks,
    testOwner?: string
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

export default function Hooks({ testId, testOwner, funcsRef, onModified }: HooksProps) {
    const [testWebHooks, setTestWebHooks] = useState<Hook[]>([])
    const isTester = useTester(testOwner)
    const hasDuplicates = new Set(testWebHooks.map(h => h.type + "_" + h.url)).size !== testWebHooks.length

    const dispatch = useDispatch<TestDispatch>()
    useEffect(() => {
        if (!testId || !isTester) {
            return
        }
        api.fetchHooks(testId).then(
            response => {
                setTestWebHooks(
                    response.map((h: Hook) => {
                        return {
                            ...h,
                            id: Number(h.id),
                            url: String(h.url),
                            active: Boolean(h.active),
                            target: Number(h.target),
                            type: String(h.type),
                        }
                    })
                )
            },
            error => dispatch(alertAction("HOOK_FETCH", "Failed to fetch webhooks", error))
        )
    }, [testId, isTester, dispatch])

    funcsRef.current = {
        save: () => dispatch(updateHooks(testId, testWebHooks)).catch(noop),
        reset: () => {
            // Perform a deep copy of the view object to prevent modifying store
            setTestWebHooks(JSON.parse(JSON.stringify(testWebHooks)) as Hook[])
        },
    }

    return (
        <>
            {hasDuplicates && (
                <Alert variant={AlertVariant.warning} title="Some webhooks contain duplicates">
                    Some webooks contain duplicate combinations of URLs and event types. Please correct these.
                </Alert>
            )}
            <div
                style={{
                    marginTop: "16px",
                    marginBottom: "16px",
                    width: "100%",
                    display: "flex",
                    justifyContent: "space-between",
                }}
            >
                <Title headingLevel="h3">Webhooks</Title>
                <Button
                    onClick={() => {
                        const newWebHook: Hook = {
                            id: 0,
                            url: "",
                            type: testHookEventTypes[0],
                            target: testId,
                            active: true,
                        }
                        setTestWebHooks([...testWebHooks, newWebHook])
                        onModified(true)
                    }}
                >
                    New Webhook
                </Button>
            </div>

            {(!testWebHooks || testWebHooks.length === 0) && "The are no Webhooks defined"}

            <DataList aria-label="List of variables">
                {testWebHooks
                    .filter(curHook => curHook.active)
                    .map((curHook, i) => (
                        <DataListItem key={i} aria-labelledby="">
                            <DataListItemRow>
                                <DataListItemCells
                                    dataListCells={[
                                        <DataListCell key="content">
                                            <HookComponentForm
                                                c={curHook}
                                                onChange={() => {
                                                    setTestWebHooks([...testWebHooks])
                                                    onModified(true)
                                                }}
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
                                            variant="primary"
                                            onClick={() => {
                                                testWebHooks[i].active = false
                                                setTestWebHooks([...testWebHooks])
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

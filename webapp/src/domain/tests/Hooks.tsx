import React, { useEffect, useRef, useState } from 'react';
import { useDispatch } from 'react-redux'
import {
    Button,
    DataList,
    DataListAction,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    Form,
    FormGroup,
    Tab,
    Tabs,
    TextInput, FormSelect, FormSelectOption, Title,
} from '@patternfly/react-core';

import { useTester } from '../../auth'

import { alertAction } from '../../alerts'
import { TestDispatch } from './reducers';
import { Hook } from "../hooks/reducers";
import { TabFunctionsRef } from './Test'
import {updateHooks} from './actions'
import { ValueGetter } from '../../components/Editor/monaco/Editor'
import * as api from "../hooks/api";
import {testHookEventTypes} from "../hooks/AllHooks";


type HookComponentFormProps = {
    c: Hook,
    onChange(): void,
    isTester: boolean,
}



const HookComponentForm = ({ c, onChange, isTester } : HookComponentFormProps) => {
    const [eventType,setEventType] = useState(c.type)

    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", float: "left", marginBottom: "25px" }}>
            <FormGroup label="Webhook URL" fieldId="url">
                <TextInput value={ c.url || "" } placeholder="e.g. 'http://myserver/api/hook'"
                           id="header"
                           onChange={ value => {
                               c.url = value
                               onChange()
                           }}
                           validated={ !!c.url  && c.url.trim() !== "" ? "default" : "error" }
                           isReadOnly={!isTester} />
            </FormGroup>
            <FormGroup label="Event Type" fieldId="event-type">
                <FormSelect
                    id="type"
                    validated={"default"}
                    value={eventType}
                    onChange={ value => {
                        c.type = value
                        setEventType(value)
                        onChange()
                    }}
                    aria-label="Event Type"
                >
                    {testHookEventTypes.map((option, index)=>{
                        return (<FormSelectOption
                            key={index}
                            value={option}
                            label={option}  />)
                    })}
                </FormSelect>
            </FormGroup>
        </Form>
    )

}

type HooksProps = {
    testId: number,
    // testWebHooks: TestWebHooks,
    testOwner?: string,
    funcsRef: TabFunctionsRef,
    onModified(modified: boolean): void,
}

export default ({ testId, testOwner, funcsRef, onModified }: HooksProps) => {
    const [testWebHooks, setTestWebHooks] = useState<Hook[]>([])
    const isTester = useTester(testOwner)
    const renderRefs = useRef(new Array<ValueGetter | undefined>(testWebHooks.length));

    useEffect(() => {
        if (!testId) {
            return
        }
        api.fetchHooks(testId).then(
            response => {
                setTestWebHooks(response.map((h: Hook) => {
                    let hd = {
                        ...h,
                        id: Number(h.id),
                        url: String(h.url),
                        active: Boolean(h.active),
                        target: Number(h.target),
                        type: String(h.type),
                    }
                    return hd
                }))

                // setGroups(groupNames(response))
                // calculations.current.splice(0)
                // response.forEach((_: any) => calculations.current.push(undefined));
            },
            error => dispatch(alertAction("HOOK_FETCH", "Failed to fetch webhooks", error))
        )
    }, [testId])


    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<TestDispatch>()
    funcsRef.current = {
        save: () => thunkDispatch(updateHooks(testId, testWebHooks)).catch(
            error => {
                dispatch(alertAction("HOOK_UPDATE", "Hook update failed", error))
                return Promise.reject()
            }
        ),
        reset: () => {
            // Perform a deep copy of the view object to prevent modifying store
            setTestWebHooks(JSON.parse(JSON.stringify(testWebHooks)) as Hook[])
        }
    }

    return (<>
        <div style={{
            marginTop: "16px",
            marginBottom: "16px",
            width: "100%",
            display: "flex",
            justifyContent: "space-between",
        }} >
            <Title headingLevel="h3">Webhooks</Title>
            <Button onClick={ () => {
                const newWebHook:Hook = {
                    id: 0,
                    url: "",
                    type: testHookEventTypes[0],
                    target: testId,
                    active: true,
                }
                setTestWebHooks([...testWebHooks, newWebHook])
                onModified(true)
                renderRefs.current.push(undefined)
            }} >New Webhook</Button>

        </div>

        { (!testWebHooks || testWebHooks.length === 0) && "The are no Webhooks defined" }

        <DataList aria-label="List of variables">
            { testWebHooks.filter(curHook => curHook.active).map((curHook, i) => (
                <DataListItem key={i} aria-labelledby="" >
                    <DataListItemRow>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="content">
                                <HookComponentForm
                                    c={curHook}
                                    onChange={() => {
                                        setTestWebHooks([...testWebHooks])
                                        onModified(true)
                                    }}
                                    isTester={isTester}
                                />
                            </DataListCell>
                        ]}/>
                        {isTester && <DataListAction
                            style={{
                                flexDirection: "column",
                                justifyContent: "center",
                            }}
                            id="delete"
                            aria-labelledby="delete"
                            aria-label="Settings actions"
                            isPlainButtonAction>
                            <Button
                                style={{width: "110%"}}
                                variant="primary"
                                onClick={() => {
                                    testWebHooks[i].active = false;
                                    setTestWebHooks([...testWebHooks])
                                    onModified(true)
                                }}>Delete</Button>
                        </DataListAction>}
                    </DataListItemRow>
                </DataListItem>

            ))}
        </DataList>
    </>)
}
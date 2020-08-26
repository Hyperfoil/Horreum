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
    TextInput,
} from '@patternfly/react-core';
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor'

import { useTester } from '../../auth'

import { alertAction } from '../../alerts'
import Accessors from '../../components/Accessors'
import { View, ViewComponent, TestDispatch } from './reducers';
import { TabFunctionsRef } from './Test'
import { updateView } from './actions'

function swap(array: any[], i1: number, i2: number) {
    const temp = array[i1]
    array[i1] = array[i2]
    array[i2] = temp
}

type ViewComponentFormProps = {
    c: ViewComponent,
    onChange(): void,
    isTester: boolean,
    setRenderGetter(vg: ValueGetter): void,
}

const ViewComponentForm = ({ c, onChange, isTester, setRenderGetter } : ViewComponentFormProps) => {
    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", float: "left", marginBottom: "25px" }}>
            <FormGroup label="Header" fieldId="header">
                <TextInput value={ c.headerName || "" } placeholder="e.g. 'Run duration'"
                        id="header"
                        onChange={ value => {
                            c.headerName = value
                            onChange()
                        }}
                        validated={ !!c.headerName && c.headerName.trim() !== "" ? "default" : "error" }
                        isReadOnly={!isTester} />
            </FormGroup>
            <FormGroup label="Accessors" fieldId="accessor">
                <Accessors
                        value={ (c.accessors && c.accessors.split(/[,;] */).map(a => a.trim()).filter(a => a.length !== 0)) || [] }
                        onChange={ value => {
                            c.accessors = value.join(";")
                            onChange()
                        }}
                        isReadOnly={!isTester} />
            </FormGroup>
            <FormGroup label="Rendering" fieldId="rendering">
                { !c.render || c.render === "" ? (
                    <Button
                        variant="link"
                        onClick={() => {
                            c.render = "(value, run, token) => value"
                            onChange()
                        }}
                    >Add render function...</Button>
                ) : (
                    <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                        { /* TODO: call onModified(true) */ }
                        <Editor value={ (c.render && c.render.toString()) || "" }
                                setValueGetter={ setRenderGetter }
                                options={{ wordWrap: 'on', wrappingIndent: 'DeepIndent', language: 'typescript', readOnly: !isTester }} />
                    </div>)
                }
            </FormGroup>
        </Form>
    )
}

type ViewsProps = {
    testId: number,
    testView: View,
    testOwner?: string,
    funcsRef: TabFunctionsRef,
    onModified(modified: boolean): void,
}

export default ({ testId, testView, testOwner, funcsRef, onModified }: ViewsProps) => {
    const isTester = useTester(testOwner)
    const renderRefs = useRef(new Array<ValueGetter | undefined>(testView.components.length));
    const [view, setView] = useState(testView)
    const updateRenders = () => view.components.forEach((_, i) => {
        view.components[i].render = renderRefs.current[i]?.getValue()
   })


    useEffect(() => {
        // Perform a deep copy of the view object to prevent modifying store
        setView(JSON.parse(JSON.stringify(testView)) as View)
    }, [testView])

    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<TestDispatch>()
    funcsRef.current = {
        save: () => thunkDispatch(updateView(testId, view)).catch(
            error => {
                dispatch(alertAction("VIEW_UPDATE", "View update failed", error))
                return Promise.reject()
            }
        ),
        reset: () => {
            // Perform a deep copy of the view object to prevent modifying store
            setView(JSON.parse(JSON.stringify(testView)) as View)
        }
    }

    return (<>
        { /* TODO: display more than default view */ }
        { /* TODO: make tabs secondary, but boxed? */ }
        <Tabs>
            <Tab key="__default" eventKey={0} title="Default" />
            <Tab key="__new" eventKey={1} title="+" />
        </Tabs>
        { isTester && <div style={{ width: "100%", textAlign: "right" }}>
            <Button onClick={ () => {
               const components = view.components
               components.push({
                   headerName: "",
                   accessors: "",
                   render: "",
                   headerOrder: components.length
               })
               setView({ ...view, components })
               onModified(true)
               renderRefs.current.push(undefined)
            }} >Add component</Button>
        </div> }
        { (!view.components || view.components.length === 0) && "The view is not defined" }
        <DataList aria-label="List of variables">
            { view.components.map((c, i) => (
                <DataListItem key={i} aria-labelledby="">
                    <DataListItemRow>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="content">
                                <ViewComponentForm
                                    c={c}
                                    onChange={ () => {
                                        setView({ ...view })
                                        onModified(true)
                                    }}
                                    isTester={ isTester }
                                    setRenderGetter={ getter => {
                                        renderRefs.current[i] = getter
                                    }}/>
                            </DataListCell>
                        ]} />
                        { isTester && <DataListAction
                            style={{
                                flexDirection: "column",
                                justifyContent: "center",
                            }}
                            id="delete"
                            aria-labelledby="delete"
                            aria-label="Settings actions"
                            isPlainButtonAction>
                            <Button
                                style={{ width: "51%" }}
                                variant="control"
                                isDisabled={ i === 0 }
                                onClick={ () => {
                                    updateRenders()
                                    swap(view.components, i - 1, i)
                                    c.headerOrder = i - 1;
                                    view.components[i].headerOrder = i;
                                    setView({ ...view })
                                    onModified(true)
                            }} >Move up</Button>
                            <Button
                                style={{ width: "51%" }}
                                variant="primary"
                                onClick={() => {
                                    view.components.splice(i, 1)
                                    renderRefs.current.splice(i, 1)
                                    view.components.forEach((c, i) => c.headerOrder = i)
                                    setView({ ...view})
                                    onModified(true)
                                }}
                            >Delete</Button>
                            <Button
                                style={{ width: "51%" }}
                                variant="control"
                                isDisabled={ i === view.components.length - 1 }
                                onClick={ () => {
                                    updateRenders()
                                    swap(view.components, i + 1, i)
                                    c.headerOrder = i + 1;
                                    view.components[i].headerOrder = i;
                                    setView({ ...view})
                                    onModified(true)
                                }} >Move down</Button>
                        </DataListAction> }
                    </DataListItemRow>
                </DataListItem>
            ))}
        </DataList>
    </>)
}
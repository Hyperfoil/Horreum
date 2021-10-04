import { useEffect, useState } from "react"
import { useDispatch } from "react-redux"
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
} from "@patternfly/react-core"
import { NavLink } from "react-router-dom"

import { useTester } from "../../auth"

import { alertAction } from "../../alerts"
import Accessors from "../../components/Accessors"
import OptionalFunction from "../../components/OptionalFunction"
import { View, ViewComponent, TestDispatch } from "./reducers"
import { TabFunctionsRef } from "./Test"
import { updateView } from "./actions"

function swap(array: any[], i1: number, i2: number) {
    const temp = array[i1]
    array[i1] = array[i2]
    array[i2] = temp
}

type ViewComponentFormProps = {
    c: ViewComponent
    onChange(): void
    isTester: boolean
}

function checkComponent(v: ViewComponent) {
    if (!v.accessors || v.accessors.length === 0) {
        return "View component requires at least one accessor"
    } else if (v.accessors.split(";").length > 1 && !v.render) {
        return "View component defines multiple accessors but does not define any function to combine these."
    } else if (v.accessors.endsWith("[]") && !v.render) {
        return "View component fetches all matches but does not define any function to combine these."
    }
}

const ViewComponentForm = ({ c, onChange, isTester }: ViewComponentFormProps) => {
    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", float: "left", marginBottom: "25px" }}>
            <FormGroup label="Header" fieldId="header">
                <TextInput
                    value={c.headerName || ""}
                    placeholder="e.g. 'Run duration'"
                    id="header"
                    onChange={value => {
                        c.headerName = value
                        onChange()
                    }}
                    validated={!!c.headerName && c.headerName.trim() !== "" ? "default" : "error"}
                    isReadOnly={!isTester}
                />
            </FormGroup>
            <FormGroup label="Accessors" fieldId="accessor">
                <Accessors
                    value={
                        (c.accessors &&
                            c.accessors
                                .split(/[,;] */)
                                .map(a => a.trim())
                                .filter(a => a.length !== 0)) ||
                        []
                    }
                    onChange={value => {
                        c.accessors = value.join(";")
                        onChange()
                    }}
                    error={checkComponent(c)}
                    isReadOnly={!isTester}
                />
            </FormGroup>
            <FormGroup label="Rendering" fieldId="rendering">
                <OptionalFunction
                    func={c.render === undefined ? undefined : c.render.toString()}
                    onChange={value => {
                        c.render = value
                        onChange()
                    }}
                    defaultFunc="(value, run, token) => value"
                    addText="Add render function..."
                    undefinedText="No render function."
                    readOnly={!isTester}
                />
            </FormGroup>
        </Form>
    )
}

type ViewsProps = {
    testId: number
    testView: View
    testOwner?: string
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

function deepCopy(view: View): View {
    const str = JSON.stringify(view, (_, val) => (typeof val === "function" ? val + "" : val))
    return JSON.parse(str) as View
}

export default function Views({ testId, testView, testOwner, funcsRef, onModified }: ViewsProps) {
    const isTester = useTester(testOwner)
    const [view, setView] = useState(deepCopy(testView))

    useEffect(() => {
        // Perform a deep copy of the view object to prevent modifying store
        setView(deepCopy(testView))
    }, [testView])

    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<TestDispatch>()
    funcsRef.current = {
        save: () => {
            for (const c of view.components) {
                if (c.accessors.trim() === "") {
                    dispatch(
                        alertAction(
                            "VIEW_UPDATE",
                            "Column " + c.headerName + " is invalid; must set at least one accessor.",
                            undefined
                        )
                    )
                    return Promise.reject()
                }
            }
            return thunkDispatch(updateView(testId, view)).catch(error => {
                dispatch(
                    alertAction(
                        "VIEW_UPDATE",
                        "View update failed. It is possible that some schema extractors used in this view do not use valid JSON paths.",
                        error
                    )
                )
                return Promise.reject()
            })
        },
        reset: () => setView(deepCopy(testView)),
    }

    return (
        <>
            {/* TODO: display more than default view */}
            {/* TODO: make tabs secondary, but boxed? */}
            <Tabs>
                <Tab key="__default" eventKey={0} title="Default" />
                <Tab key="__new" eventKey={1} title="+" />
            </Tabs>
            <div style={{ width: "100%", textAlign: "right" }}>
                {isTester && (
                    <Button
                        onClick={() => {
                            const components = view.components
                            components.push({
                                headerName: "",
                                accessors: "",
                                render: "",
                                headerOrder: components.length,
                            })
                            setView({ ...view, components })
                            onModified(true)
                        }}
                    >
                        Add component
                    </Button>
                )}
                <NavLink className="pf-c-button pf-m-secondary" to={"/run/list/" + testId}>
                    Go to runs
                </NavLink>
            </div>
            {(!view.components || view.components.length === 0) && "The view is not defined"}
            <DataList aria-label="List of variables">
                {view.components.map((c, i) => (
                    <DataListItem key={i} aria-labelledby="">
                        <DataListItemRow>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key="content">
                                        <ViewComponentForm
                                            c={c}
                                            onChange={() => {
                                                setView({ ...view })
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
                                        style={{ width: "51%" }}
                                        variant="control"
                                        isDisabled={i === 0}
                                        onClick={() => {
                                            swap(view.components, i - 1, i)
                                            c.headerOrder = i - 1
                                            view.components[i].headerOrder = i
                                            setView({ ...view })
                                            onModified(true)
                                        }}
                                    >
                                        Move up
                                    </Button>
                                    <Button
                                        style={{ width: "51%" }}
                                        variant="primary"
                                        onClick={() => {
                                            view.components.splice(i, 1)
                                            view.components.forEach((c, i) => (c.headerOrder = i))
                                            setView({ ...view })
                                            onModified(true)
                                        }}
                                    >
                                        Delete
                                    </Button>
                                    <Button
                                        style={{ width: "51%" }}
                                        variant="control"
                                        isDisabled={i === view.components.length - 1}
                                        onClick={() => {
                                            swap(view.components, i + 1, i)
                                            c.headerOrder = i + 1
                                            view.components[i].headerOrder = i
                                            setView({ ...view })
                                            onModified(true)
                                        }}
                                    >
                                        Move down
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

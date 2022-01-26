import { useMemo, useState, useEffect } from "react"
import { useDispatch, useSelector } from "react-redux"
import { useHistory } from "react-router"

import { useTester } from "../../auth"
import { alertAction } from "../../alerts"
import * as api from "../alerting/api"
import { NavLink } from "react-router-dom"
import { Variable } from "../alerting/types"

import {
    Alert,
    AlertActionCloseButton,
    Bullseye,
    Button,
    EmptyState,
    ExpandableSection,
    Form,
    FormGroup,
    Modal,
    Select,
    SelectOption,
    SimpleList,
    SimpleListGroup,
    SimpleListItem,
    Spinner,
    Split,
    SplitItem,
    TextInput,
    Title,
} from "@patternfly/react-core"

import { PlusCircleIcon } from "@patternfly/react-icons"

import Accessors from "../../components/Accessors"
import LogSlider from "../../components/LogSlider"
import OptionalFunction from "../../components/OptionalFunction"
import RecalculateModal from "../alerting/RecalculateModal"
import TestSelect, { SelectedTest } from "../../components/TestSelect"
import CalculationLogModal from "./CalculationLogModal"
import { subscriptions as subscriptionsSelector } from "./selectors"
import { TabFunctionsRef } from "../../components/SavedTabs"

type TestSelectModalProps = {
    isOpen: boolean
    onClose(): void
    onConfirm(testId: number, group: string | undefined): Promise<any>
}

const CopyVarsModal = ({ isOpen, onClose, onConfirm }: TestSelectModalProps) => {
    const [test, setTest] = useState<SelectedTest>()
    const [working, setWorking] = useState(false)
    const [selectGroupOpen, setSelectGroupOpen] = useState(false)
    const [groups, setGroups] = useState<string[]>([])
    const [group, setGroup] = useState<string>()
    const reset = () => {
        setTest(undefined)
        setWorking(false)
        setGroups([])
        setGroup(undefined)
        onClose()
    }
    return (
        <Modal
            className="foobar"
            variant="small"
            title="Copy regression variables from..."
            isOpen={isOpen}
            onClose={reset}
            actions={[
                <Button
                    isDisabled={!test || working}
                    onClick={() => {
                        setWorking(true)
                        onConfirm(test?.id || -1, group === "<all groups>" ? undefined : group).finally(reset)
                    }}
                >
                    Copy
                </Button>,
                <Button isDisabled={working} variant="secondary" onClick={reset}>
                    Cancel
                </Button>,
            ]}
        >
            {working && <Spinner />}
            {!working && (
                <>
                    <TestSelect
                        selection={test}
                        onSelect={t => {
                            setTest(t)
                            setGroups([])
                            api.fetchVariables(t.id).then(response => setGroups(groupNames(response)))
                        }}
                        placeholderText="Select..."
                    />
                    {test && groups.length > 0 && (
                        <Select
                            isOpen={selectGroupOpen}
                            onToggle={setSelectGroupOpen}
                            selections={group}
                            onSelect={(_, item) => {
                                setGroup(item as string)
                                setSelectGroupOpen(false)
                            }}
                        >
                            {[
                                <SelectOption key={"all"} value="<all groups>" />,
                                ...groups.map(group => <SelectOption key={group} value={group} />),
                            ]}
                        </Select>
                    )}
                </>
            )}
        </Modal>
    )
}

type RenameGroupModalProps = {
    isOpen: boolean
    groups: string[]
    onRename(from: string, to: string): void
    onClose(): void
}

const RenameGroupModal = (props: RenameGroupModalProps) => {
    const [from, setFrom] = useState<string>()
    const [to, setTo] = useState<string>()
    const [selectOpen, setSelectOpen] = useState(false)
    return (
        <Modal
            variant="small"
            title="Rename group"
            isOpen={props.isOpen}
            onClose={props.onClose}
            actions={[
                <Button
                    isDisabled={!from || !to}
                    onClick={() => {
                        props.onRename(from as string, to as string)
                        props.onClose()
                    }}
                >
                    Rename
                </Button>,
                <Button
                    variant="secondary"
                    onClick={() => {
                        props.onClose()
                    }}
                >
                    Cancel
                </Button>,
            ]}
        >
            <Form>
                <FormGroup label="Existing group" fieldId="from">
                    <Select
                        placeholderText="Select group..."
                        isOpen={selectOpen}
                        onToggle={setSelectOpen}
                        selections={from}
                        onSelect={(_, item) => {
                            setFrom(item as string)
                            setSelectOpen(false)
                        }}
                    >
                        {props.groups.map(group => (
                            <SelectOption key={group} value={group} />
                        ))}
                    </Select>
                </FormGroup>
                <FormGroup label="New group name" fieldId="to">
                    <TextInput value={to} id="to" onChange={setTo} />
                </FormGroup>
            </Form>
        </Modal>
    )
}

type VariableFormProps = {
    variable: Variable
    isTester: boolean
    groups: string[]
    setGroups(gs: string[]): void
    onChange(v: Variable): void
}

function checkVariable(v: Variable) {
    if (!v.accessors || v.accessors.length === 0) {
        return "Variable requires at least one accessor"
    } else if (v.accessors.split(";").length > 1 && !v.calculation) {
        return "Variable defines multiple accessors but does not define any function to combine these."
    } else if (v.accessors.endsWith("[]") && !v.calculation) {
        return "Variable fetches all matches but does not define any function to combine these."
    }
}

const VariableForm = (props: VariableFormProps) => {
    const [isExpanded, setExpanded] = useState(false)
    const [groupOpen, setGroupOpen] = useState(false)
    return (
        <Form id={`variable-${props.variable.id}`} isHorizontal={true}>
            <FormGroup label="Name" fieldId="name">
                <TextInput
                    value={props.variable.name || ""}
                    id="name"
                    onChange={value => props.onChange({ ...props.variable, name: value })}
                    validated={!!props.variable.name && props.variable.name.trim() !== "" ? "default" : "error"}
                    isReadOnly={!props.isTester}
                />
            </FormGroup>
            <FormGroup label="Group" fieldId="group">
                <Select
                    variant="typeahead"
                    typeAheadAriaLabel="Select group"
                    onToggle={setGroupOpen}
                    onSelect={(e, group, isPlaceholder) => {
                        setGroupOpen(false)
                        props.onChange({ ...props.variable, group: isPlaceholder ? undefined : group.toString() })
                    }}
                    onClear={() => {
                        props.onChange({ ...props.variable, group: undefined })
                    }}
                    selections={props.variable.group}
                    isOpen={groupOpen}
                    placeholderText="-none-"
                    isCreatable={true}
                    onCreateOption={option => {
                        props.setGroups([...props.groups, option].sort())
                    }}
                >
                    {props.groups.map((g, index) => (
                        <SelectOption key={index} value={g} />
                    ))}
                </Select>
            </FormGroup>
            <FormGroup label="Accessors" fieldId="accessor">
                <Accessors
                    value={
                        (props.variable.accessors &&
                            props.variable.accessors
                                .split(/[,;] */)
                                .map(a => a.trim())
                                .filter(a => a.length !== 0)) ||
                        []
                    }
                    error={checkVariable(props.variable)}
                    onChange={value => {
                        props.onChange({ ...props.variable, accessors: value.join(";") })
                    }}
                    isReadOnly={!props.isTester}
                />
            </FormGroup>
            <FormGroup label="Calculation" fieldId="calculation">
                <OptionalFunction
                    func={props.variable.calculation === undefined ? undefined : props.variable.calculation.toString()}
                    onChange={value => props.onChange({ ...props.variable, calculation: value })}
                    defaultFunc="value => value"
                    addText="Add calculation function..."
                    undefinedText="No calculation function"
                    readOnly={!props.isTester}
                />
            </FormGroup>
            <ExpandableSection
                toggleText={isExpanded ? "Hide settings" : "Show advanced settings"}
                onToggle={setExpanded}
                isExpanded={isExpanded}
            >
                <FormGroup
                    label="Max difference for last datapoint"
                    fieldId="maxDifferenceLastDatapoint"
                    helperText="Maximum difference between the last value and the mean of preceding values."
                >
                    <LogSlider
                        value={props.variable.maxDifferenceLastDatapoint * 100}
                        onChange={value =>
                            props.onChange({ ...props.variable, maxDifferenceLastDatapoint: value / 100 })
                        }
                        isDisabled={!props.isTester}
                        min={1}
                        max={1000}
                        unit="%"
                    />
                </FormGroup>
                <FormGroup
                    label="Min window"
                    fieldId="minWindow"
                    helperText="Minimum number of datapoints after last change to run tests against."
                >
                    <LogSlider
                        value={props.variable.minWindow}
                        onChange={minWindow => props.onChange({ ...props.variable, minWindow })}
                        isDiscrete={true}
                        isDisabled={!props.isTester}
                        min={1}
                        max={1000}
                        unit={"\u00A0"}
                    />
                </FormGroup>
                <FormGroup
                    label="Max difference for floating window"
                    fieldId="maxDifferenceFloatingWindow"
                    helperText="Maximum difference between the mean of last N datapoints in the floating window and the mean of preceding values."
                >
                    <LogSlider
                        value={props.variable.maxDifferenceFloatingWindow * 100}
                        onChange={value => {
                            props.onChange({ ...props.variable, maxDifferenceFloatingWindow: value / 100 })
                        }}
                        isDisabled={!props.isTester}
                        min={1}
                        max={1000}
                        unit="%"
                    />
                </FormGroup>
                <FormGroup
                    label="Floating window size"
                    fieldId="floatingWindow"
                    helperText="Limit the number of datapoints considered when testing for a change."
                >
                    <LogSlider
                        value={props.variable.floatingWindow}
                        onChange={value => {
                            props.onChange({ ...props.variable, floatingWindow: value })
                        }}
                        isDisabled={!props.isTester}
                        isDiscrete={true}
                        min={1}
                        max={1000}
                        unit={"\u00A0"}
                    />
                </FormGroup>
            </ExpandableSection>
        </Form>
    )
}

type VariablesProps = {
    testName: string
    testId: number
    testOwner?: string
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

type ActionsProps = {
    isTester: boolean
    testName: string
    canRename: boolean
    onCopy(): void
    onRenameGroup(): void
    onRecalculate(): void
    onShowLog(): void
}

const Actions = (props: ActionsProps) => {
    return (
        <div>
            <NavLink className="pf-c-button pf-m-primary" to={"/series?test=" + props.testName}>
                Go to series
            </NavLink>
            {props.isTester && (
                <>
                    <Button variant="secondary" onClick={props.onCopy}>
                        Copy...
                    </Button>
                    <Button variant="secondary" onClick={props.onRenameGroup} isDisabled={props.canRename}>
                        Rename group...
                    </Button>
                    <Button variant="secondary" onClick={props.onRecalculate}>
                        Recalculate
                    </Button>
                    <Button variant="secondary" onClick={props.onShowLog}>
                        Show log
                    </Button>
                </>
            )}
        </div>
    )
}

function groupNames(vars: Variable[]) {
    return [
        ...new Set<string>(
            vars
                .map(v => v.group)
                .filter(g => !!g)
                .map(g => g as string)
        ),
    ].sort()
}

export default function Variables({ testName, testId, testOwner, onModified, funcsRef }: VariablesProps) {
    const [variables, setVariables] = useState<Variable[]>([])
    const [groups, setGroups] = useState<string[]>([])
    const [selectedVariable, setSelectedVariable] = useState<Variable>()
    const [recalcConfirm, setRecalcConfirm] = useState<(_: any) => void>()
    const [ignoreNoSubscriptions, setIgnoreNoSubscriptions] = useState(false)
    const dispatch = useDispatch()
    // dummy variable to cause reloading of variables
    const [reload, setReload] = useState(0)
    useEffect(() => {
        if (!testId) {
            return
        }
        api.fetchVariables(testId).then(
            response => {
                response.forEach((v: Variable) => {
                    // convert nulls to undefined
                    if (!v.group) v.group = undefined
                })
                setVariables(response)
                if (response.length > 0) {
                    setSelectedVariable(response[0])
                }
                setGroups(groupNames(response))
            },
            error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
        )
    }, [testId, reload, dispatch])
    const isTester = useTester(testOwner)
    funcsRef.current = {
        save: () => {
            variables.forEach(v => {
                if (v.calculation === "") {
                    v.calculation = undefined
                }
            })
            return api
                .updateVariables(testId, variables)
                .catch(error => {
                    dispatch(alertAction("VARIABLE_UPDATE", "Failed to update regression variables", error))
                    return Promise.reject()
                })
                .then(_ => {
                    return new Promise(resolve => {
                        // we have to pass this using function, otherwise it would call the resolve function
                        setRecalcConfirm(() => resolve)
                    })
                })
                .then(_ => {
                    Promise.resolve()
                })
        },
        reset: () => {
            setVariables([])
            setReload(reload + 1)
        },
    }

    const [recalculateOpen, setRecalculateOpen] = useState(false)
    const [copyOpen, setCopyOpen] = useState(false)
    const addVariable = () => {
        const newVar = {
            id: Math.min(-1, ...variables.map(v => v.id - 1)),
            testid: testId,
            name: "",
            order: variables.length,
            accessors: "",
            maxDifferenceLastDatapoint: 0.2,
            minWindow: 5,
            maxDifferenceFloatingWindow: 0.1,
            floatingWindow: 5,
        }
        setVariables([...variables, newVar])
        setSelectedVariable(newVar)
        onModified(true)
    }

    const [renameGroupOpen, setRenameGroupOpen] = useState(false)
    const [isLogOpen, setLogOpen] = useState(false)
    const subscriptions = useSelector(subscriptionsSelector(testId))?.filter(s => !s.startsWith("!"))
    const hasSubscription = subscriptions && subscriptions.length > 0

    const history = useHistory()
    useEffect(() => {
        const fragmentParts = history.location.hash.split("+")
        if (fragmentParts.length === 2 && fragmentParts[0] === "#vars") {
            const component = document.getElementById("variable-" + fragmentParts[1])
            if (component) {
                component.scrollIntoView()
            }
        }
    }, [])

    const groupedVariables = useMemo(() => {
        const grouped = variables
            .reduce((a: Variable[][], v: Variable) => {
                const group = a.find(g => g[0].group === v.group)
                if (group === undefined) {
                    a.push([v])
                } else {
                    group.push(v)
                }
                return a
            }, [])
            .sort((g1, g2) => (g1[0].group || "").localeCompare(g2[0].group || ""))
        grouped.forEach(g => g.sort((v1, v2) => v1.name.localeCompare(v2.name)))
        return grouped
    }, [variables])

    if (!variables) {
        return (
            <Bullseye>
                <Spinner />
            </Bullseye>
        )
    }
    return (
        <>
            <div
                style={{
                    marginTop: "16px",
                    marginBottom: "16px",
                    width: "100%",
                    display: "flex",
                    justifyContent: "space-between",
                }}
            >
                <Title headingLevel="h3">Variables</Title>
                <Actions
                    isTester={isTester}
                    testName={testName}
                    canRename={!groups || groups.length === 0}
                    onCopy={() => setCopyOpen(true)}
                    onRenameGroup={() => setRenameGroupOpen(true)}
                    onRecalculate={() => setRecalculateOpen(true)}
                    onShowLog={() => setLogOpen(true)}
                />
            </div>
            {isTester && !hasSubscription && !ignoreNoSubscriptions && variables.length > 0 && (
                <Alert
                    variant="warning"
                    title="This test has no subscriptions"
                    actionClose={<AlertActionCloseButton onClose={() => setIgnoreNoSubscriptions(true)} />}
                >
                    This test is configured to run regression analysis but nobody is listening to change notifications.
                    Please configure interested parties in the Subscriptions tab.
                </Alert>
            )}
            <RenameGroupModal
                isOpen={renameGroupOpen}
                groups={groups}
                onClose={() => setRenameGroupOpen(false)}
                onRename={(from, to) => {
                    variables.forEach(v => {
                        if (v.group === from) {
                            v.group = to
                        }
                    })
                    setVariables([...variables])
                    setGroups([...groups.map(g => (g === from ? to : g))])
                }}
            />
            <RecalculateModal
                isOpen={!!recalcConfirm}
                onClose={() => {
                    if (recalcConfirm) {
                        recalcConfirm(false)
                    }
                    setRecalcConfirm(undefined)
                }}
                showLog={() => setLogOpen(true)}
                testId={testId}
                title="Proceed with recalculation"
                recalculate="Recalculate"
                cancel="Skip"
                message="Do you want to drop all datapoints and calculate new ones, based on the updated variables?"
            />
            <RecalculateModal
                isOpen={recalculateOpen}
                onClose={() => setRecalculateOpen(false)}
                showLog={() => setLogOpen(true)}
                testId={testId}
                title="Confirm recalculation"
                recalculate="Recalculate"
                cancel="Cancel"
                message="Really drop all datapoints, calculating new ones?"
            />
            <CopyVarsModal
                isOpen={copyOpen}
                onClose={() => setCopyOpen(false)}
                onConfirm={(otherTestId, group) => {
                    return api.fetchVariables(otherTestId).then(
                        response => {
                            const copied = group ? response.filter((v: Variable) => v.group === group) : response
                            setVariables([
                                ...variables,
                                ...copied.map((v: Variable) => ({
                                    ...v,
                                    id: -1,
                                    testid: testId,
                                })),
                            ])
                        },
                        error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
                    )
                }}
            />
            <CalculationLogModal
                isOpen={isLogOpen}
                onClose={() => setLogOpen(false)}
                testId={testId}
                source="variables"
            />
            <Split hasGutter>
                <SplitItem>
                    {groupedVariables && groupedVariables.length > 0 && (
                        <SimpleList
                            onSelect={(_, props) => setSelectedVariable(variables.find(v => v.id === props.itemId))}
                            isControlled={false}
                        >
                            {groupedVariables.map((g, j) => (
                                <SimpleListGroup key={j} title={g[0].group || "(no group)"}>
                                    {g.map((v, i) => (
                                        <SimpleListItem key={i} itemId={v.id} isActive={selectedVariable?.id === v.id}>
                                            {v.name || (
                                                <span style={{ color: "#888" }}>(please set variable name)</span>
                                            )}
                                        </SimpleListItem>
                                    ))}
                                </SimpleListGroup>
                            ))}
                            <Button variant="link" onClick={addVariable}>
                                <PlusCircleIcon />
                                {"\u00A0"}Add new variable...
                            </Button>
                        </SimpleList>
                    )}
                </SplitItem>
                <SplitItem isFilled>
                    {!selectedVariable && (
                        <Bullseye>
                            <EmptyState>No variables</EmptyState>
                        </Bullseye>
                    )}
                    {selectedVariable && (
                        <>
                            <div style={{ textAlign: "right" }}>
                                <Button
                                    variant="danger"
                                    onClick={() => {
                                        const newVars = variables.filter(v => v !== selectedVariable)
                                        setVariables(newVars)
                                        setSelectedVariable(newVars.length > 0 ? newVars[0] : undefined)
                                    }}
                                >
                                    Delete
                                </Button>
                            </div>
                            <VariableForm
                                variable={selectedVariable}
                                isTester={isTester}
                                onChange={value => {
                                    setSelectedVariable(value)
                                    const newVars = variables.filter(v => v.id !== value.id)
                                    newVars.push(value)
                                    setVariables(newVars)
                                    onModified(true)
                                }}
                                groups={groups}
                                setGroups={setGroups}
                            />
                        </>
                    )}
                </SplitItem>
            </Split>
        </>
    )
}

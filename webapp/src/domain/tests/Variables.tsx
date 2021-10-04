import { useState, useEffect } from 'react';
import { useDispatch } from 'react-redux';

import { useTester } from '../../auth'
import { alertAction } from '../../alerts'
import * as api from '../alerting/api'
import { NavLink } from 'react-router-dom'
import { Variable } from '../alerting/types'

import {
    Bullseye,
    Button,
    DataList,
    DataListAction,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    ExpandableSection,
    Form,
    FormGroup,
    Modal,
    Select,
    SelectOption,
    Spinner,
    TextInput,
    Title,
} from '@patternfly/react-core';

import Accessors from '../../components/Accessors'
import LogSlider from '../../components/LogSlider'
import OptionalFunction from '../../components/OptionalFunction'
import RecalculateModal from '../alerting/RecalculateModal'
import TestSelect, { SelectedTest } from '../../components/TestSelect'
import CalculationLogModal from './CalculationLogModal'
import { TabFunctionsRef } from './Test'

type TestSelectModalProps = {
    isOpen: boolean,
    onClose(): void
    onConfirm(testId: number, group: string | undefined): Promise<any>
}

const CopyVarsModal = ({isOpen, onClose, onConfirm}: TestSelectModalProps) => {
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
    return (<Modal
        className="foobar"
        variant="small"
        title="Copy regression variables from..."
        isOpen={isOpen}
        onClose={ reset }
        actions={[
            <Button
                isDisabled={ !test || working }
                onClick={ () => {
                    setWorking(true)
                    onConfirm(test?.id || -1, group === "<all groups>" ? undefined : group).finally(reset)
                }}
            >Copy</Button>,
            <Button
                isDisabled={working}
                variant="secondary"
                onClick={ reset }
            >Cancel</Button>
        ]}
    >
        { working && <Spinner /> }
        { !working && <>
            <TestSelect
                selection={test}
                onSelect={t => {
                    setTest(t)
                    setGroups([])
                    api.fetchVariables(t.id)
                        .then(response => setGroups(groupNames(response)))
                }}
                placeholderText="Select..."
            />
            { test && groups.length > 0 &&
                <Select
                    isOpen={selectGroupOpen}
                    onToggle={setSelectGroupOpen}
                    selections={group}
                    onSelect={(_, item) => {
                        setGroup(item as string)
                        setSelectGroupOpen(false)
                    }}
                    >
                { [
                    (<SelectOption key={"all"} value="<all groups>" />),
                    ...groups.map(group => <SelectOption key={group} value={group} />)
                ] }
                </Select>
            }
        </>}
    </Modal>)
}

type RenameGroupModalProps = {
    isOpen: boolean,
    groups: string[],
    onRename(from: string, to: string): void,
    onClose(): void,
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
                    isDisabled={ !from || !to }
                    onClick={() => {
                    props.onRename(from as string, to as string)
                    props.onClose()
                }}
                >Rename</Button>,
                <Button variant="secondary" onClick={() => {
                    props.onClose()
                }}>Cancel</Button>
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
                    { props.groups.map(group => <SelectOption key={group} value={group} />) }
                    </Select>
               </FormGroup>
               <FormGroup label="New group name" fieldId="to">
                    <TextInput
                        value={ to }
                        id="to"
                        onChange={ setTo }
                    />
               </FormGroup>
            </Form>
        </Modal>
    )
}

type VariableFormProps = {
    index: number,
    variables: Variable[],
    isTester: boolean,
    groups: string[],
    setGroups(gs: string[]): void,
    onChange(): void,
}

function checkVariable(v: Variable) {
    if (!v.accessors || v.accessors.length === 0) {
        return "Variable requires at least one accessor"
    } else if (v.accessors.split(';').length > 1 && !v.calculation) {
        return "Variable defines multiple accessors but does not define any function to combine these."
    } else if (v.accessors.endsWith("[]") && !v.calculation) {
        return "Variable fetches all matches but does not define any function to combine these."
    }
}

const VariableForm = ({ index, variables, isTester, onChange, groups, setGroups }: VariableFormProps) => {
    const variable = variables[index]
    const [isExpanded, setExpanded] = useState(false)
    const [groupOpen, setGroupOpen] = useState(false)
    return <Form
        isHorizontal={true}>
        <FormGroup label="Name" fieldId="name">
            <TextInput value={ variable.name || "" }
                        id="name"
                        onChange={ value => {
                            variable.name = value
                            onChange()
                        }}
                        validated={ !!variable.name && variable.name.trim() !== "" ? "default" : "error"}
                        isReadOnly={!isTester} />
        </FormGroup>
        <FormGroup label="Group" fieldId="group">
            <Select
                variant="typeahead"
                typeAheadAriaLabel="Select group"
                onToggle={setGroupOpen}
                onSelect={(e, group, isPlaceholder) => {
                    if (isPlaceholder) {
                        variable.group = undefined
                    } else {
                        variable.group = group.toString()
                    }
                    setGroupOpen(false)
                    onChange()
                }}
                onClear={() => {
                    variable.group = undefined
                    onChange()
                }}
                selections={variable.group}
                isOpen={groupOpen}
                placeholderText="-none-"
                isCreatable={true}
                onCreateOption={ option => {
                    setGroups([ ...groups, option].sort())
                }}
            >
                { groups.map((g, index) => <SelectOption key={index} value={g} />)}
            </Select>
        </FormGroup>
        <FormGroup label="Accessors" fieldId="accessor">
            <Accessors
                        value={ (variable.accessors && variable.accessors.split(/[,;] */).map(a => a.trim()).filter(a => a.length !== 0)) || [] }
                        error={ checkVariable(variable) }
                        onChange={ value => {
                            variable.accessors = value.join(";")
                            onChange()
                        }}
                        isReadOnly={!isTester} />
        </FormGroup>
        <FormGroup label="Calculation" fieldId="calculation">
            <OptionalFunction
                func={ variable.calculation === undefined ? undefined : variable.calculation.toString() }
                onChange={ value => {
                    variable.calculation = value
                    onChange()
                }}
                defaultFunc="value => value"
                addText="Add calculation function..."
                undefinedText="No calculation function"
                readOnly={ !isTester }
            />
        </FormGroup>
        <ExpandableSection toggleText={ isExpanded ? "Hide settings" : "Show advanced settings" }
                           onToggle={setExpanded}
                           isExpanded={isExpanded} >
            <FormGroup label="Max difference for last datapoint" fieldId="maxDifferenceLastDatapoint" helperText="Maximum difference between the last value and the mean of preceding values.">
                <LogSlider
                    value={ variable.maxDifferenceLastDatapoint * 100 }
                    onChange={ value => {
                        variable.maxDifferenceLastDatapoint = value / 100
                        onChange()
                    }}
                    isDisabled={!isTester}
                    min={1}
                    max={1000}
                    unit='%'
                />
            </FormGroup>
            <FormGroup label="Min window" fieldId="minWindow" helperText="Minimum number of datapoints after last change to run tests against.">
                <LogSlider
                    value={ variable.minWindow }
                    onChange={ value => {
                        variable.minWindow = value
                        onChange()
                    }}
                    isDiscrete={true}
                    isDisabled={!isTester}
                    min={1}
                    max={1000}
                    unit={'\u00A0'}
                />
            </FormGroup>
            <FormGroup label="Max difference for floating window" fieldId="maxDifferenceFloatingWindow" helperText="Maximum difference between the mean of last N datapoints in the floating window and the mean of preceding values.">
                <LogSlider
                    value={ variable.maxDifferenceFloatingWindow * 100 }
                    onChange={ value => {
                        variable.maxDifferenceFloatingWindow = value / 100
                        onChange()
                    }}
                    isDisabled={!isTester}
                    min={1}
                    max={1000}
                    unit='%'
                />
            </FormGroup>
            <FormGroup label="Floating window size" fieldId="floatingWindow" helperText="Limit the number of datapoints considered when testing for a change.">
                <LogSlider
                    value={ variable.floatingWindow }
                    onChange={ value => {
                        variable.floatingWindow = value
                        onChange()
                    }}
                    isDisabled={!isTester}
                    isDiscrete={true}
                    min={1}
                    max={1000}
                    unit={'\u00A0'}
                />
            </FormGroup>
        </ExpandableSection>
    </Form>
}

function swap(array: any[], i1: number, i2: number) {
    const temp = array[i1]
    array[i1] = array[i2]
    array[i2] = temp
}

type VariablesProps = {
    testName: string,
    testId: number
    testOwner?: string,
    funcsRef: TabFunctionsRef,
    onModified(modified: boolean): void,
}

function sortByOrder(v1: Variable, v2: Variable) {
    if (v1.group === v2.group) {
        return v1.order - v2.order
    } else if (!v1.group) {
        return -1;
    } else if (!v2.group) {
        return 1;
    } else {
        return v1.group.localeCompare(v2.group)
    }
}

type ActionsProps = {
    isTester: boolean,
    testName: string,
    canRename: boolean,
    onAdd(): void,
    onCopy(): void,
    onRenameGroup(): void,
    onRecalculate(): void,
    onShowLog(): void,
}

const Actions = (props: ActionsProps) => {
    return (<div>
        { props.isTester && <>
            <Button onClick={props.onAdd}>Add variable</Button>
            <Button variant="secondary" onClick={ props.onCopy }>Copy...</Button>
            <Button variant="secondary" onClick={ props.onRenameGroup } isDisabled={ props.canRename }>Rename group...</Button>
            <Button variant="secondary" onClick={ props.onRecalculate }>Recalculate</Button>
            <Button variant="secondary" onClick={ props.onShowLog }>Show log</Button>
        </>}
        <NavLink className="pf-c-button pf-m-secondary" to={ "/series?test=" + props.testName }>Go to series</NavLink>
    </div>)
}

function groupNames(vars: Variable[]) {
    return  [ ...new Set<string>(vars.map(v => v.group)
        .filter(g => !!g).map(g => g as string))].sort();
}

export default function Variables({ testName, testId, testOwner, onModified, funcsRef }: VariablesProps) {
    const [variables, setVariables] = useState<Variable[]>([])
    const [groups, setGroups] = useState<string[]>([])
    const [recalcConfirm, setRecalcConfirm] = useState<(_: any) => void>()
    const dispatch = useDispatch()
    // dummy variable to cause reloading of variables
    const [ reload, setReload ] = useState(0)
    useEffect(() => {
        if (!testId) {
            return
        }
        api.fetchVariables(testId).then(
            response => {
                setVariables(response.sort(sortByOrder))
                setGroups(groupNames(response))
            },
            error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
        )
    }, [testId, reload, dispatch])
    const isTester = useTester(testOwner)
    funcsRef.current = {
        save: () => {
            variables.forEach((v, i) => {
                v.order = i
                if (v.calculation === "") {
                    v.calculation = undefined
                }
            })
            return api.updateVariables(testId, variables).catch(
                error => {
                    dispatch(alertAction("VARIABLE_UPDATE", "Failed to update regression variables", error))
                    return Promise.reject()
                }
            ).then(_ => {
                return new Promise((resolve, reject) => {
                    // we have to pass this using function, otherwise it would call the resolve function
                    setRecalcConfirm(() => resolve)
                })
            }).then(_ => {
                Promise.resolve()
            })
        },
        reset: () => {
            setVariables([])
            setReload(reload + 1)
        }
    }

    const [recalculateOpen, setRecalculateOpen] = useState(false)
    const [copyOpen, setCopyOpen ] = useState(false)
    const addVariable = () => {
        variables?.push({
            id: -1,
            testid: testId,
            name: "",
            order: variables.length,
            accessors: "",
            maxDifferenceLastDatapoint: 0.2,
            minWindow: 5,
            maxDifferenceFloatingWindow: 0.1,
            floatingWindow: 5
        })
        setVariables([ ...variables])
        onModified(true)
    }

    const [renameGroupOpen, setRenameGroupOpen] = useState(false)
    const [isLogOpen, setLogOpen] = useState(false)
    if (!variables) {
        return <Bullseye><Spinner /></Bullseye>
    }
    return (<>
        <div style={{
            marginTop: "16px",
            marginBottom: "16px",
            width: "100%",
            display: "flex",
            justifyContent: "space-between",
        }} >
            <Title headingLevel="h3">Variables</Title>
            <Actions
                isTester={isTester}
                testName={testName}
                canRename={!groups || groups.length === 0}
                onAdd={ addVariable }
                onCopy={() => setCopyOpen(true)}
                onRenameGroup={ () => setRenameGroupOpen(true) }
                onRecalculate={ () => setRecalculateOpen(true) }
                onShowLog={ () => setLogOpen(true) }
            />
        </div>
        <RenameGroupModal
            isOpen={renameGroupOpen}
            groups={groups}
            onClose={() => setRenameGroupOpen(false)}
            onRename={(from, to) => {
                variables.forEach(v => {
                    if (v.group === from) {
                        v.group = to;
                    }
                })
                setVariables([ ...variables ])
                setGroups([ ...groups.map(g => g === from ? to : g) ])
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
            showLog={ () => setLogOpen(true) }
            testId={testId}
            title="Proceed with recalculation"
            recalculate="Recalculate"
            cancel="Skip"
            message="Do you want to drop all datapoints and calculate new ones, based on the updated variables?"
            />
        <RecalculateModal
            isOpen={recalculateOpen}
            onClose={() => setRecalculateOpen(false)}
            showLog={ () => setLogOpen(true) }
            testId={testId}
            title="Confirm recalculation"
            recalculate="Recalculate"
            cancel="Cancel"
            message="Really drop all datapoints, calculating new ones?"
            />
        <CopyVarsModal
            isOpen={copyOpen}
            onClose={() => setCopyOpen(false) }
            onConfirm={(otherTestId, group) => {
                return api.fetchVariables(otherTestId).then(
                    response => {
                        const copied = group ? response.filter((v: Variable) => v.group === group) : response
                        setVariables([ ...variables, ...copied.sort(sortByOrder).map((v: Variable) => ({
                            ...v,
                            id: -1,
                            testid: testId,
                            maxDifferenceLastDatapointStr: String(v.maxDifferenceLastDatapoint),
                            minWindowStr: String(v.minWindow),
                            maxDifferenceFloatingWindowStr: String(v.maxDifferenceFloatingWindow),
                            floatingWindowStr: String(v.floatingWindow),
                        }))])
                    },
                    error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
                )
            }} />
        <CalculationLogModal
            isOpen={isLogOpen}
            onClose={ () => setLogOpen(false) }
            testId={testId}
            />
        <DataList aria-label="List of variables">
            { variables?.map((_, i) => (
                <DataListItem key={i} aria-labelledby="">
                    <DataListItemRow>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="content">
                                <VariableForm
                                    index={i}
                                    variables={variables}
                                    isTester={isTester}
                                    onChange={() => {
                                        setVariables([ ...variables ])
                                        onModified(true)
                                    }}
                                    groups={groups}
                                    setGroups={setGroups}
                                />
                            </DataListCell>
                        ]} />
                        { isTester &&
                        <DataListAction
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
                                isDisabled={i === 0}
                                variant="control"
                                onClick={() => {
                                    swap(variables, i - 1, i)
                                    setVariables([ ...variables ])
                                    onModified(true)
                                }}
                            >Move up</Button>
                            <Button
                                style={{ width: "51%" }}
                                variant="primary"
                                onClick={() => {
                                    variables.splice(i, 1)
                                    setVariables([ ...variables ])
                                    onModified(true)
                                }}
                            >Delete</Button>
                            <Button
                                style={{ width: "51%" }}
                                isDisabled={i === variables.length - 1}
                                variant="control"
                                onClick={() => {
                                    swap(variables, i + 1, i)
                                    setVariables([ ...variables ])
                                    onModified(true)
                                }}
                            >Move down</Button>
                        </DataListAction>
                        }
                    </DataListItemRow>
                </DataListItem>
            ))}
        </DataList>
        <div style={{ textAlign: "right", marginTop: "16px"}} >
            <Actions
                isTester={isTester}
                testName={testName}
                canRename={!groups || groups.length === 0}
                onAdd={ addVariable }
                onCopy={() => setCopyOpen(true)}
                onRenameGroup={ () => setRenameGroupOpen(true) }
                onRecalculate={() => setRecalculateOpen(true) }
                onShowLog={ () => setLogOpen(true) }
            />
        </div>
    </>)
}
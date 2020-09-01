import React, { useState, useRef, useEffect } from 'react';
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
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor'
import RecalculateModal from '../alerting/RecalculateModal'
import TestSelect, { SelectedTest } from '../../components/TestSelect'
import { TabFunctionsRef } from './Test'

const NO_LIMIT = "<no limit>"

type TestSelectModalProps = {
    isOpen: boolean,
    onClose(): void
    onConfirm(testId: number): Promise<any>
}

const TestSelectModal = ({isOpen, onClose, onConfirm}: TestSelectModalProps) => {
    const [test, setTest] = useState<SelectedTest>()
    const [working, setWorking] = useState(false)
    return (<Modal
        className="foobar"
        variant="small"
        title="Copy regression variables from..."
        isOpen={isOpen}
        onClose={ () => {
            setTest(undefined)
            onClose()
        }}
        actions={[
            <Button
                isDisabled={ !test || working }
                onClick={ () => {
                    setWorking(true)
                    onConfirm(test?.id || -1).finally(() => {
                        setTest(undefined)
                        setWorking(false)
                        onClose()
                    })
                }}
            >Copy</Button>,
            <Button
                isDisabled={working}
                variant="secondary"
                onClick={ () => {
                    setTest(undefined)
                    onClose()
                }}
            >Cancel</Button>
        ]}
    >
        { working && <Spinner /> }
        { !working &&
            <TestSelect
                selection={test}
                onSelect={setTest}
                placeholderText="Select..."
                direction="up" />
        }
    </Modal>)
}

type VariableDisplay = {
    maxWindowStr: string,
    deviationFactorStr: string,
    confidenceStr: string,
} & Variable;

type VariableFormProps = {
    index: number,
    variables: VariableDisplay[],
    calculations:(ValueGetter | undefined)[],
    isTester: boolean,
    groups: string[],
    setGroups(gs: string[]): void,
    onChange(): void,
}

const VariableForm = ({ index, variables, calculations, isTester, onChange, groups, setGroups }: VariableFormProps) => {
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
                        onChange={ value => {
                            variable.accessors = value.join(";")
                            onChange()
                        }}
                        isReadOnly={!isTester} />
        </FormGroup>
        <ExpandableSection toggleText={ isExpanded ? "Hide settings" : "Show advanced settings" }
                           onToggle={setExpanded}
                           isExpanded={isExpanded} >
            <FormGroup label="Calculation" fieldId="calculation">
                <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                    { /* TODO: call onModified(true) */ }
                    <Editor value={ (variable.calculation && variable.calculation.toString()) || "" }
                            setValueGetter={e => { calculations[index] = e }}
                            options={{ wordWrap: 'on', wrappingIndent: 'DeepIndent', language: 'typescript', readOnly: !isTester }} />
                </div>
            </FormGroup>
            { /* TODO: use sliders when Patternfly 4 has them */ }
            <FormGroup label="Max window" fieldId="maxWindow">
                <TextInput value={ variable.maxWindowStr }
                            id="maxWindow"
                            onChange={ value => {
                                variable.maxWindowStr = value
                                variable.maxWindow = value === NO_LIMIT ? 0x7FFFFFFF : parseInt(value)
                                onChange()
                            }}
                            validated={ /^\([0-9]+\)|<no limit>$/.test(variable.maxWindowStr) ? "default" : "error" }
                            isReadOnly={!isTester} />
            </FormGroup>
            <FormGroup label="Deviation factor" fieldId="deviationFactor">
                <TextInput value={ variable.deviationFactorStr }
                            id="deviationFactor"
                            onChange={ value => {
                                variable.deviationFactorStr = value
                                variable.deviationFactor = parseFloat(value)
                                onChange()
                            }}
                            validated={ /^[0-9]+(\.[0-9]+)?$/.test(variable.deviationFactorStr) && variable.deviationFactor > 0 ? "default" : "error" }
                            isReadOnly={!isTester} />
            </FormGroup>
            <FormGroup label="Confidence" fieldId="confidence">
                <TextInput value={ variable.confidenceStr }
                            id="confidence"
                            onChange={ value => {
                                variable.confidenceStr = value
                                variable.confidence = parseFloat(value)
                                onChange()
                            }}
                            validated={ /^[0-9]+(\.[0-9]+)?$/.test(variable.confidenceStr) && variable.confidence > 0.5 && variable.confidence < 1.0 ? "default" : "error" }
                            isReadOnly={!isTester} />
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

export default ({ testName, testId, testOwner, onModified, funcsRef }: VariablesProps) => {
    const [variables, setVariables] = useState<VariableDisplay[]>([])
    const [groups, setGroups] = useState<string[]>([])
    const calculations = useRef(new Array<ValueGetter | undefined>())
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
                setVariables(response.map((v: Variable) => {
                    let vd: VariableDisplay = {
                        ...v,
                        maxWindowStr: v.maxWindow === 0x7FFFFFFF ? NO_LIMIT : String(v.maxWindow),
                        deviationFactorStr: String(v.deviationFactor),
                        confidenceStr: String(v.confidence),
                    }
                    return vd
                }).sort((v1: VariableDisplay, v2: VariableDisplay) => {
                    if (v1.group == v2.group) {
                        return v1.order - v2.order
                    } else if (!v1.group) {
                        return -1;
                    } else if (!v2.group) {
                        return 1;
                    } else {
                        return v1.group.localeCompare(v2.group)
                    }
                }))
                setGroups([... new Set(variables.map(v => v.group).filter(g => !!g).map(g => g as string))].sort())
                calculations.current.splice(0)
                response.forEach((_: any) => calculations.current.push(undefined));
            },
            error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
        )
    }, [testId, reload])
    const isTester = useTester(testOwner)
    funcsRef.current = {
        save: () => {
            variables.forEach((v, i) => {
                v.calculation = calculations.current[i]?.getValue()
                v.order = i
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
            calculations.current.splice(0)
            setReload(reload + 1)
        }
    }


    if (!variables) {
        return <Bullseye><Spinner /></Bullseye>
    }
    const [recalculateOpen, setRecalculateOpen] = useState(false)
    const [copyOpen, setCopyOpen ] = useState(false)
    return (<>
        <div style={{
            marginTop: "16px",
            marginBottom: "16px",
            width: "100%",
            display: "flex",
            justifyContent: "space-between",
        }} >
            <Title headingLevel="h3">Variables</Title>
            <div>
            { isTester && <>
                <Button onClick={ () => {
                    variables?.push({
                        id: -1,
                        testid: testId,
                        name: "",
                        order: variables.length,
                        accessors: "",
                        maxWindowStr: NO_LIMIT,
                        maxWindow: 0,
                        deviationFactorStr: "2.0",
                        deviationFactor: 2.0,
                        confidenceStr: "0.95",
                        confidence: 0.95,
                    })
                    calculations.current.push(undefined)
                    setVariables([ ...variables])
                    onModified(true)
                }}>Add variable</Button>
                <Button variant="secondary"
                    onClick={ () => setCopyOpen(true) }
                >Copy...</Button>
                <Button
                    variant="secondary"
                    onClick={ () => setRecalculateOpen(true) }
                >Recalculate</Button>
            </>}
            <NavLink className="pf-c-button pf-m-secondary" to={ "/series?test=" + testName }>Go to series</NavLink>
            </div>
        </div>
        <RecalculateModal
            isOpen={!!recalcConfirm}
            onClose={() => {
                if (recalcConfirm) {
                    recalcConfirm(false)
                }
                setRecalcConfirm(undefined)
            }}
            testId={testId}
            title="Proceed with recalculation"
            recalculate="Recalculate"
            cancel="Skip"
            message="Do you want to drop all datapoints and calculate new ones, based on the updated variables?"
            />
        <RecalculateModal
            isOpen={recalculateOpen}
            onClose={() => setRecalculateOpen(false)}
            testId={testId}
            title="Confirm recalculation"
            recalculate="Recalculate"
            cancel="cancel"
            message="Really drop all datapoints, calculating new ones?"
            />
        <TestSelectModal
            isOpen={copyOpen}
            onClose={() => setCopyOpen(false) }
            onConfirm={otherTestId => {
                return api.fetchVariables(otherTestId).then(
                    response => {
                        setVariables([ ...variables, ...response.map((v: Variable) => ({
                            ...v,
                            id: -1,
                            testid: testId,
                            maxWindowStr: v.maxWindow === 0x7FFFFFFF ? NO_LIMIT : String(v.maxWindow),
                            deviationFactorStr: String(v.deviationFactor),
                            confidenceStr: String(v.confidence),
                        }))])
                        response.forEach((_: Variable) => calculations.current.push(undefined))
                    },
                    error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
                )
            }} />
        <DataList aria-label="List of variables">
            { variables?.map((_, i) => (
                <DataListItem key={i} aria-labelledby="">
                    <DataListItemRow>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="content">
                                <VariableForm
                                    index={i}
                                    variables={variables}
                                    calculations={calculations.current}
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
                                    variables[i - 1].calculation = calculations.current[i - 1]?.getValue()
                                    variables[i].calculation = calculations.current[i]?.getValue()
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
                                    calculations.current.splice(i, 1)
                                    setVariables([ ...variables ])
                                    onModified(true)
                                }}
                            >Delete</Button>
                            <Button
                                style={{ width: "51%" }}
                                isDisabled={i === variables.length - 1}
                                variant="control"
                                onClick={() => {
                                    variables[i + 1].calculation = calculations.current[i + 1]?.getValue()
                                    variables[i].calculation = calculations.current[i]?.getValue()
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
    </>)
}
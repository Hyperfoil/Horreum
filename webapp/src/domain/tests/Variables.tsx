import React, { useState, useRef, useEffect, MutableRefObject } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import { isTesterSelector } from '../../auth'
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
    Form,
    FormGroup,
    Spinner,
    TextInput,
    Title,
} from '@patternfly/react-core';

import Accessors from '../../components/Accessors'
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor'
import RecalculateModal from '../alerting/RecalculateModal'

type VariablesProps = {
    testName: string,
    testId: number
    saveHookRef: MutableRefObject<((_: number) => Promise<void>) | undefined>
}

type VariableDisplay = {
    maxWindowStr: string,
    deviationFactorStr: string,
    confidenceStr: string,
} & Variable;

type VariableFormProps = {
    index: number,
    variables: VariableDisplay[],
    setVariables(vs: VariableDisplay[]): void,
    calculations:(ValueGetter | undefined)[]
}

const VariableForm = ({ index, variables, setVariables, calculations }: VariableFormProps) => {
    const isTester = useSelector(isTesterSelector)
    const variable = variables[index]
    return <Form
        isHorizontal={true}>
        <FormGroup label="Name" fieldId="name">
            <TextInput value={ variable.name || "" }
                        id="name"
                        onChange={ value => {
                            variable.name = value
                            setVariables([ ...variables])
                        }}
                        validated={ !!variable.name && variable.name.trim() !== "" ? "default" : "error"}
                        isReadOnly={!isTester} />
        </FormGroup>
        <FormGroup label="Accessors" fieldId="accessor">
            <Accessors
                        value={ (variable.accessors && variable.accessors.split(/[,;] */).map(a => a.trim()).filter(a => a.length !== 0)) || [] }
                        onChange={ value => { variable.accessors = value.join(";"); }}
                        isReadOnly={!isTester} />
        </FormGroup>
        <FormGroup label="Calculation" fieldId="calculation">
            <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
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
                            variable.maxWindow = parseInt(value)
                            setVariables([ ...variables])
                        }}
                        validated={ /^[0-9]+$/.test(variable.maxWindowStr) ? "default" : "error" }
                        isReadOnly={!isTester} />
        </FormGroup>
        <FormGroup label="Deviation factor" fieldId="deviationFactor">
            <TextInput value={ variable.deviationFactorStr }
                        id="deviationFactor"
                        onChange={ value => {
                            variable.deviationFactorStr = value
                            variable.deviationFactor = parseFloat(value)
                            setVariables([ ...variables])
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
                            setVariables([ ...variables])
                        }}
                        validated={ /^[0-9]+(\.[0-9]+)?$/.test(variable.confidenceStr) && variable.confidence > 0.5 && variable.confidence < 1.0 ? "default" : "error" }
                        isReadOnly={!isTester} />
        </FormGroup>
    </Form>
}

export default ({ testName, testId, saveHookRef }: VariablesProps) => {
    const [variables, setVariables] = useState<VariableDisplay[]>([])
    const calculations = new Array<ValueGetter | undefined>()
    const dispatch = useDispatch()
    useEffect(() => {
        if (!testId) {
            return
        }
        api.fetchVariables(testId).then(
            response => {
                setVariables(response.map((v: Variable) => {
                    let vd: VariableDisplay = {
                        ...v,
                        maxWindowStr: String(v.maxWindow),
                        deviationFactorStr: String(v.deviationFactor),
                        confidenceStr: String(v.confidence),
                    }
                    return vd
                }))
                calculations.splice(0)
                variables?.forEach(_ => calculations.push(undefined));
            },
            error => dispatch(alertAction("VARIABLE_FETCH", "Failed to fetch regression variables", error))
        )
    }, [testId])
    const isTester = useSelector(isTesterSelector)
    saveHookRef.current = updatedTestId => api.updateVariables(updatedTestId, variables).catch(
        error => {
            dispatch(alertAction("VARIABLE_UPDATE", "Failed to update regression variables", error))
            return Promise.reject()
        }
    )

    if (!variables) {
        return <Bullseye><Spinner /></Bullseye>
    }

    const [recalculateOpen, setRecalculateOpen] = useState(false)
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
                        accessors: "",
                        maxWindowStr: "",
                        maxWindow: -1,
                        deviationFactorStr: "2.0",
                        deviationFactor: 2.0,
                        confidenceStr: "0.95",
                        confidence: 0.95,
                    })
                    calculations.push(undefined)
                    setVariables([ ...variables])
                }}>Add variable</Button>
                <Button
                    variant="secondary"
                    onClick={ () => setRecalculateOpen(true) }
                >Recalculate</Button>
            </>}
            <NavLink className="pf-c-button pf-m-secondary" to={ "/series?test=" + testName }>Go to series</NavLink>
            </div>
        </div>
        <RecalculateModal
            isOpen={recalculateOpen}
            onClose={() => setRecalculateOpen(false)}
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
                                    setVariables={setVariables}
                                    calculations={calculations}
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
                                variant="primary"
                                onClick={() => {
                                    variables.splice(i, 1)
                                    calculations.splice(i, 1)
                                    setVariables([ ...variables ])
                                }}
                            >Delete</Button>
                        </DataListAction>
                        }
                    </DataListItemRow>
                </DataListItem>
            ))}
        </DataList>
    </>)
}
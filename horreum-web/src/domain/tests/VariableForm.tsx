import { useState, useEffect, ReactNode } from "react"

import {ChangeDetection, ConditionConfig, Variable, } from "../../api"

import {
	ActionList,
	Button,
	Flex,
	FlexItem,
	Form,
	FormGroup,
	Popover,
	Tab,
	Tabs,
	TabTitleIcon,
	TabTitleText,
	TextInput,
	Title
} from '@patternfly/react-core';

import { AddCircleOIcon } from "@patternfly/react-icons"

import ConditionComponent from "../../components/ConditionComponent"
import Labels from "../../components/Labels"
import OptionalFunction from "../../components/OptionalFunction"
import HelpButton from "../../components/HelpButton"
import { TypeaheadSelect } from "../../components/templates/TypeahedSelect"
import { SimpleSelect } from "../../components/templates/SimpleSelect"

type VariableFormProps = {
    variable: Variable
    isTester: boolean
    groups: string[]
    setGroups(gs: string[]): void
    onChange(v: Variable): void
    models: ConditionConfig[]
}

function checkVariable(v: Variable) {
    if (!v.labels || v.labels.length === 0) {
        return "Variable requires at least one label"
    } else if (v.labels.length > 1 && !v.calculation) {
        return "Variable defines multiple labels but does not define any function to combine these."
    }
}

export default function VariableForm(props: VariableFormProps) {
    const [changeDetection, setChangeDetection] = useState<ChangeDetection>()
    const [newModel, setNewModel] = useState<string>()
    useEffect(() => {
        const rds = [...props.variable.changeDetection]
        if (!changeDetection || !rds.includes(changeDetection)) {
            setChangeDetection(rds[0])
        }
    }, [props.variable])
    const usedModel = props.models.find(m => m.name === changeDetection?.model)
    const update = (rd: ChangeDetection) => {
        const newArray = [...props.variable.changeDetection]
        const index = newArray.findIndex(o => o.id === rd.id)
        if (index < 0) {
            newArray.push(rd)
        } else {
            newArray[index] = rd
        }
        props.onChange({ ...props.variable, changeDetection: newArray })
        setChangeDetection(rd)
    }
    const tabs : any = props.variable.changeDetection.map((rd, i): ReactNode => (
        <Tab
            key={i}
            eventKey={i}
            title={(props.models.find(m => m.name === rd.model)?.title || rd.model) + ` (${i + 1})`}
        />
    ));
    return (
        <Form id={`variable-${props.variable.id}`} isHorizontal>
            <FormGroup label="Name" fieldId="name">
                <TextInput
                    value={props.variable.name || ""}
                    id="name"
                    onChange={(_event, value) => props.onChange({ ...props.variable, name: value })}
                    validated={!!props.variable.name && props.variable.name.trim() !== "" ? "default" : "error"}
                    //isReadOnly={!props.isTester} this is no longe rsupport
                    readOnlyVariant={props.isTester ? undefined : "default"}
                    //  {"default"}
                />
            </FormGroup>
            <FormGroup label="Group" fieldId="group">
                <TypeaheadSelect
                    placeholder="-none-"
                    initialOptions={props.groups.map(g => ({value: g, content: g, selected: g === props.variable.group}))}
                    onSelect={(_, item) => {
                        if (!props.groups.includes(item as string)) {
                            props.setGroups([...props.groups, item as string].sort())
                        }
                        props.onChange({...props.variable, group: item === "-none-" ? undefined : item as string})
                    }}
                    onClearSelection={() => props.onChange({...props.variable, group: undefined})}
                    selected={props.variable.group}
                    isCreatable
                    isScrollable
                    maxMenuHeight="40vh"
                    toggleWidth="100%"
                    popperProps={{enableFlip: false, preventOverflow: true}}
                />
            </FormGroup>
            <FormGroup label="Labels" fieldId="labels">
                <Labels
                    labels={props.variable.labels}
                    error={checkVariable(props.variable)}
                    onChange={labels => {
                        props.onChange({ ...props.variable, labels })
                    }}
                    isReadOnly={!props.isTester}
                    defaultMetrics={true}
                    defaultFiltering={false}
                />
            </FormGroup>
            <FormGroup label="Calculation" fieldId="calculation">
                <OptionalFunction
                    func={props.variable.calculation}
                    onChange={func => props.onChange({ ...props.variable, calculation: func })}
                    defaultFunc="value => value"
                    addText="Add calculation function..."
                    undefinedText="No calculation function"
                    readOnly={!props.isTester}
                />
            </FormGroup>
            <Title headingLevel="h3">Conditions</Title>
            <Tabs
                activeKey={changeDetection ? props.variable.changeDetection.indexOf(changeDetection) : "__add"}
                onSelect={(e, index) => {
                    e.preventDefault()
                    setChangeDetection(index === "__add" ? undefined : props.variable.changeDetection[index as number])
                }}
            >
                {tabs}
                {props.isTester ? (
                    <Tab
                        eventKey="__add"
                        title={
                            <>
                                <TabTitleIcon>
                                    <AddCircleOIcon />
                                </TabTitleIcon>
                                <TabTitleText>Add...</TabTitleText>
                            </>
                        }
                    >
                        <ActionList>
                            <SimpleSelect
                                initialOptions={
                                    props.models.map(m => ({value: m.name, content: m.title, selected: m.name === newModel}))
                                }
                                selected={newModel}
                                onSelect={(_, value) => setNewModel(value as string)}
                            />
                            <Button
                                isDisabled={!newModel}
                                onClick={() => {
                                    update({
                                        id: Math.min(-1, ...props.variable.changeDetection.map(rd => rd.id - 1)),
                                        model: newModel || "",
                                        config: JSON.parse(
                                            JSON.stringify(props.models.find(m => m.name === newModel)?.defaults)
                                        ),
                                    })
                                    setNewModel(undefined)
                                }}
                            >
                                Add new condition
                            </Button>
                        </ActionList>
                    </Tab>
                ) : undefined}
            </Tabs>

            {usedModel && changeDetection && (
                <>
                    <FormGroup label="Model" fieldId="model">
                        <Flex justifyContent={{ default: "justifyContentSpaceBetween" }}>
                            <FlexItem
                                style={{ paddingTop: "var(--pf-t--global--spacer--control--vertical--default)" }}
                            >
                                {usedModel.title}
                                <Popover headerContent={usedModel.title} bodyContent={usedModel.description}>
                                    <HelpButton />
                                </Popover>
                            </FlexItem>
                            {props.isTester && (
                                <FlexItem>
                                    <Button
                                        variant="danger"
                                        onClick={() => {
                                            const newArray = props.variable.changeDetection.filter(
                                                rd => rd !== changeDetection
                                            )
                                            props.onChange({ ...props.variable, changeDetection: newArray })
                                            setChangeDetection(newArray.length > 0 ? newArray[0] : undefined)
                                        }}
                                    >
                                        Delete condition
                                    </Button>
                                </FlexItem>
                            )}
                        </Flex>
                    </FormGroup>
                    {usedModel.ui.map(comp => (
                        <ConditionComponent
                            {...comp}
                            isTester={props.isTester}
                            //we are using ignore because we don't know the model type to cast or declare the type info
                            // @ts-ignore
                            value={changeDetection.config[comp.name]}
                            onChange={value => {
                                const copy = { ...changeDetection }
                                // @ts-ignore
                                copy.config[comp.name] = value
                                update(copy)
                            }}
                        />
                    ))}
                </>
            )}
        </Form>
    )
}

import { useState, useEffect } from "react"

import { ChangeDetection, ConditionConfig, Variable } from "../../api"

import {
    ActionList,
    Button,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    Popover,
    Select,
    SelectOption,
    Tab,
    Tabs,
    TabTitleIcon,
    TabTitleText,
    TextInput,
    Title,
} from "@patternfly/react-core"

import { AddCircleOIcon } from "@patternfly/react-icons"

import ConditionComponent from "../../components/ConditionComponent"
import EnumSelect from "../../components/EnumSelect"
import Labels from "../../components/Labels"
import OptionalFunction from "../../components/OptionalFunction"
import HelpButton from "../../components/HelpButton"

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
    const [groupOpen, setGroupOpen] = useState(false)
    const [changeDetection, setChangeDetection] = useState<ChangeDetection>()
    const [adding, setAdding] = useState(false)
    const [newModel, setNewModel] = useState<string>()
    useEffect(() => {
        if (!changeDetection && !adding) {
            const rds = [...props.variable.changeDetection]
            setChangeDetection(rds.length > 0 ? rds[0] : undefined)
        }
    }, [props.variable, props.variable.changeDetection, changeDetection])
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
                    if (index === "__add") {
                        setChangeDetection(undefined)
                        setAdding(true)
                    } else {
                        setChangeDetection(props.variable.changeDetection[index as number])
                        setAdding(false)
                    }
                }}
            >
                {props.variable.changeDetection.map((rd, i) => (
                    <Tab
                        key={i}
                        eventKey={i}
                        title={(props.models.find(m => m.name === rd.model)?.title || rd.model) + ` (${i + 1})`}
                    />
                ))}
                {props.isTester && (
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
                            <EnumSelect
                                options={props.models.reduce((acc, m) => {
                                    acc[m.name] = m.title
                                    return acc
                                }, {} as any)}
                                selected={newModel}
                                onSelect={setNewModel}
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
                )}
            </Tabs>

            {usedModel && changeDetection && (
                <>
                    <FormGroup label="Model" fieldId="model">
                        <Flex justifyContent={{ default: "justifyContentSpaceBetween" }}>
                            <FlexItem
                                style={{ paddingTop: "var(--pf-c-form--m-horizontal__group-label--md--PaddingTop)" }}
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
                            value={changeDetection.config[comp.name]}
                            onChange={value => {
                                const copy = { ...changeDetection }
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

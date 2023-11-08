import {useState, useEffect, useContext} from "react"
import { NavLink } from "react-router-dom"

import {
    Alert,
    Button,
    Flex,
    FlexItem,
    FormGroup,
    FormSection,
    List,
    ListItem,
    Popover,
    Select,
    SelectOption,
    Tab,
    Tabs,
    TextInput,
} from "@patternfly/react-core"

import {alertingApi, ConditionConfig, experimentApi, Test, Variable} from "../../api"
import { useTester } from "../../auth"
import HelpButton from "../../components/HelpButton"
import Labels from "../../components/Labels"
import { TabFunctionsRef } from "../../components/SavedTabs"
import SplitForm from "../../components/SplitForm"
import { ExperimentProfile } from "../../generated/models/ExperimentProfile"
import OptionalFunction from "../../components/OptionalFunction"
import ConditionComponent from "../../components/ConditionComponent"
import EnumSelect from "../../components/EnumSelect"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type ExperimentsProps = {
    test?: Test
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

export default function Experiments(props: ExperimentsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const isTester = useTester(props.test?.owner)
    const [profiles, setProfiles] = useState<ExperimentProfile[]>([])
    const [selected, setSelected] = useState<ExperimentProfile>()
    const [deleted, setDeleted] = useState<number[]>([])
    const [loading, setLoading] = useState(false)
    const [models, setModels] = useState<ConditionConfig[]>([])
    const [variables, setVariables] = useState<Variable[]>([])
    const [activeCondition, setActiveCondition] = useState<number | string>()
    const [modelToAdd, setModelToAdd] = useState<string>()
    const [resetCounter, setResetCounter] = useState(0)

    useEffect(() => {
        const testId = props.test?.id
        if (!testId) {
            return
        }
        setLoading(true)
        Promise.all([
            experimentApi.profiles(testId).then(
                ps => {
                    setProfiles(ps)
                    if (ps.length > 0) {
                        setSelected(ps[0])
                    }
                },
                error =>
                    alerting.dispatchError( error, "FETCH_EXPERIMENT_PROFILES", "Cannot fetch experiment profiles.")
            ),
            alertingApi.variables(testId).then(setVariables, error =>
                alerting.dispatchError( error, "FETCH_VARIABLES", "Cannot fetch change detection variables")
            ),
        ]).finally(() => setLoading(false))
    }, [props.test?.id, resetCounter])
    useEffect(() => {
        experimentApi.models().then(setModels, error =>
            alerting.dispatchError( error, "FETCH_EXPERIMENT_MODELS", "Cannot fetch experiment condition models")
        )
    }, [])
    useEffect(() => {
        if (selected && activeCondition === undefined) {
            setActiveCondition(selected.comparisons.length > 0 ? 0 : "__add")
        }
    }, [selected])

    props.funcsRef.current = {
        save: () => {
            const testId = props.test?.id
            if (!testId) {
                return Promise.reject()
            }
            return Promise.all([
                ...profiles
                    .filter(p => Object.prototype.hasOwnProperty.call(p, "modified"))
                    .map(p =>
                        experimentApi.addOrUpdateProfile(testId, p).then(
                            id => {
                                p.id = id
                                alerting.dispatchInfo(
                                    "EPERIMENT_PROFILE_SAVED",
                                    "Profile " + p.name + " was saved.",
                                    "",
                                    3000
                                )
                            },
                            error =>
                                alerting.dispatchError(
                                    error,
                                    "EPERIMENT_PROFILE_SAVE",
                                    "Failed to save experiment profile " + p.name + "."
                                )
                        )
                    ),
                ...deleted.map(id => {
                    experimentApi.deleteProfile(id, testId).then(
                        () =>
                            alerting.dispatchInfo(
                                "EPERIMENT_PROFILE_DELETED",
                                "Profile " + id + " was deleted.",
                                "",
                                3000
                            ),
                        error =>
                            alerting.dispatchError(
                                error,
                                "EPERIMENT_PROFILE_SAVE",
                                "Failed to delete experiment profile " + id + "."
                            )
                    )
                }),
            ]).then(() => setDeleted([]))
        },
        reset: () => {
            setDeleted([])
            setResetCounter(resetCounter + 1)
        },
    }

    function update(patch: Partial<ExperimentProfile>) {
        if (!selected) {
            return
        }
        const updated = { ...selected, ...patch, modified: true }
        setSelected(updated)
        setProfiles(profiles.map(p => (p.id === updated.id ? updated : p)))
        props.onModified(true)
    }

    return (
        <SplitForm
            itemType="Profile"
            addItemText="Add new profile"
            canAddItem={isTester}
            newItem={id => ({
                id,
                name: "",
                selectorLabels: [],
                baselineLabels: [],
                comparisons: [],
            })}
            noItemTitle="No experiments"
            noItemText="This test does not define any experiments yet."
            canDelete={isTester}
            onDelete={profile => {
                setDeleted([...deleted, profile.id])
            }}
            items={profiles}
            onChange={setProfiles}
            selected={selected}
            onSelected={p => setSelected(p)}
            loading={loading}
        >
            {selected && (
                <>
                    <FormGroup label="Profile name" fieldId="name" isRequired>
                        <TextInput
                            id="name"
                            isRequired={true}
                            isReadOnly={!isTester}
                            value={selected.name}
                            onChange={name => update({ name })}
                        />
                    </FormGroup>
                    <FormSection
                        title={
                            <>
                                Experiment selector
                                <Popover
                                    headerContent="Experiment selector labels and function"
                                    bodyContent={
                                        <div>
                                            This part defines which datasets are subject to this experiment profile.
                                            <List>
                                                <ListItem>
                                                    Labels select data from the dataset based on its{" "}
                                                    <NavLink to="/schema">schema(s)</NavLink>.
                                                </ListItem>
                                                <ListItem>
                                                    Filtering function takes the label value (in case of single label)
                                                    or object keyed by label names (in case of multiple labels) as its
                                                    only parameter and returns true if the dataset should be subject to
                                                    the experiment.
                                                </ListItem>
                                            </List>
                                        </div>
                                    }
                                >
                                    <HelpButton />
                                </Popover>
                            </>
                        }
                    >
                        <FormGroup label="Labels" fieldId="selectorLabels" isRequired>
                            <Labels
                                labels={selected.selectorLabels}
                                onChange={selectorLabels => update({ selectorLabels })}
                                error={
                                    selected.selectorLabels.length === 0
                                        ? "You need to set label(s) for selecting experiment datasets."
                                        : undefined
                                }
                                isReadOnly={!isTester}
                                defaultMetrics={false}
                                defaultFiltering={true}
                            />
                        </FormGroup>
                        <FormGroup label="Filter" fieldId="selectorFilter">
                            <OptionalFunction
                                func={selected.selectorFilter}
                                onChange={selectorFilter => update({ selectorFilter })}
                                readOnly={!isTester}
                                undefinedText="No filter defined"
                                addText="Set filtering function"
                                defaultFunc="value => !!value"
                            />
                        </FormGroup>
                    </FormSection>
                    <FormSection
                        title={
                            <>
                                Baseline
                                <Popover
                                    headerContent="Baseline labels and function"
                                    bodyContent={
                                        <div>
                                            Datapoints from this experiment are compared against datapoints from other
                                            datasets, called the baseline. Note that experiment datasets and baseline
                                            should be naturally disjunct.
                                            <List>
                                                <ListItem>
                                                    Labels select data from the dataset based on its{" "}
                                                    <NavLink to="/schema">schema(s)</NavLink>.
                                                </ListItem>
                                                <ListItem>
                                                    Filtering function takes the label value (in case of single label)
                                                    or object keyed by label names (in case of multiple labels) as its
                                                    only parameter and returns true if the dataset should be part of the
                                                    baseline.
                                                </ListItem>
                                            </List>
                                        </div>
                                    }
                                >
                                    <HelpButton />
                                </Popover>
                            </>
                        }
                    >
                        <FormGroup label="Labels" fieldId="baselineLabels" isRequired>
                            <Labels
                                labels={selected.baselineLabels}
                                onChange={baselineLabels => update({ baselineLabels })}
                                error={
                                    selected.baselineLabels.length === 0
                                        ? "You need to set label(s) for selecting baseline datasets."
                                        : undefined
                                }
                                isReadOnly={!isTester}
                                defaultMetrics={false}
                                defaultFiltering={true}
                            />
                        </FormGroup>
                        <FormGroup label="Filter" fieldId="baselineFilter">
                            <OptionalFunction
                                func={selected.baselineFilter}
                                onChange={baselineFilter => update({ baselineFilter })}
                                readOnly={!isTester}
                                undefinedText="No filter defined"
                                addText="Set filtering function"
                                defaultFunc="value => !!value"
                            />
                        </FormGroup>
                    </FormSection>
                    <FormSection title="Comparisons">
                        <Tabs
                            activeKey={activeCondition}
                            onSelect={(e, key) => {
                                e.preventDefault()
                                setActiveCondition(key)
                            }}
                        >
                            {selected.comparisons.map((c, i) => {
                                const usedModel = models.find(m => m.name === c.model)
                                if (!usedModel) {
                                    return <Tab eventKey={i} title="<unknown>" />
                                }
                                return (
                                    <Tab eventKey={i} title={`${usedModel.title} (${i})`}>
                                        <FormGroup label="model" fieldId="model">
                                            <Flex justifyContent={{ default: "justifyContentSpaceBetween" }}>
                                                <FlexItem
                                                    style={{
                                                        paddingTop:
                                                            "var(--pf-c-form--m-horizontal__group-label--md--PaddingTop)",
                                                    }}
                                                >
                                                    {usedModel.title}
                                                    <Popover
                                                        headerContent={usedModel.title}
                                                        bodyContent={usedModel.description}
                                                    >
                                                        <HelpButton />
                                                    </Popover>
                                                </FlexItem>
                                                {isTester && (
                                                    <FlexItem>
                                                        <Button
                                                            variant="danger"
                                                            onClick={() => {
                                                                selected.comparisons.splice(i, 1)
                                                                update({ comparisons: selected.comparisons })
                                                            }}
                                                        >
                                                            Delete condition
                                                        </Button>
                                                    </FlexItem>
                                                )}
                                            </Flex>
                                        </FormGroup>
                                        <VariableSelect
                                            variables={variables}
                                            selectedId={c.variableId}
                                            onChange={v => {
                                                c.variableId = v.id
                                                update({ comparisons: [...selected.comparisons] })
                                            }}
                                        />
                                        {usedModel.ui.map(comp => (
                                            <ConditionComponent
                                                {...comp}
                                                isTester={isTester}
                                                value={(c.config as any)[comp.name]}
                                                onChange={value => {
                                                    (c.config as any)[comp.name] = value
                                                    update({ comparisons: [...selected.comparisons] })
                                                }}
                                            />
                                        ))}
                                    </Tab>
                                )
                            })}
                            <Tab key="__add" eventKey="__add" title="Add comparison">
                                <FormGroup label="Model" fieldId="model">
                                    <EnumSelect
                                        options={models.reduce((acc, m) => ({ ...acc, [m.name]: m.title }), {})}
                                        selected={modelToAdd}
                                        onSelect={setModelToAdd}
                                    />
                                </FormGroup>
                                {variables.length === 0 && (
                                    <Alert variant="warning" title="Change detection does not define any variables">
                                        Experiments share variable definitions from Change Detection; you don't have any
                                        variables defined There and therefore you cannot add any conditions. Please
                                        defined Change Detection variables first.
                                    </Alert>
                                )}
                                <div style={{ width: "100%", textAlign: "center", marginTop: "16px" }}>
                                    <Button
                                        isDisabled={!modelToAdd || variables.length === 0}
                                        onClick={() => {
                                            if (modelToAdd && variables.length > 0) {
                                                update({
                                                    comparisons: [
                                                        ...(selected.comparisons as any),
                                                        {
                                                            model: modelToAdd,
                                                            config:
                                                                models.find(m => m.name === modelToAdd)?.defaults || "",
                                                            variableId: variables[0].id,
                                                        },
                                                    ],
                                                })
                                                setActiveCondition(selected.comparisons.length)
                                            }
                                        }}
                                    >
                                        Add comparison
                                    </Button>
                                    {/* TODO: add comparison for all variables */}
                                </div>
                            </Tab>
                        </Tabs>
                    </FormSection>
                    <FormGroup
                        label={
                            <>
                                Extra labels{" "}
                                <Popover
                                    headerContent="Extra labels"
                                    bodyContent="These labels are not used by Horreum but are added to the result event and therefore can be used e.g. when firing an Action."
                                >
                                    <HelpButton />
                                </Popover>
                            </>
                        }
                        fieldId="extraLabels"
                    >
                        <Labels
                            labels={selected.extraLabels || []}
                            onChange={extraLabels => update({ extraLabels })}
                            isReadOnly={!isTester}
                            defaultMetrics={true}
                            defaultFiltering={true}
                        />
                    </FormGroup>
                </>
            )}
        </SplitForm>
    )
}

type VariableSelectProps = {
    variables: Variable[]
    selectedId: number | undefined
    onChange(variable: Variable): void
}

function VariableSelect(props: VariableSelectProps) {
    const [open, setOpen] = useState(false)
    return (
        <Select
            isOpen={open}
            onToggle={setOpen}
            selections={props.variables.find(v => v.id === props.selectedId)?.name}
            onSelect={(_, name) => {
                const v = props.variables.find(v => v.name === name)
                if (v) {
                    props.onChange(v)
                }
                setOpen(false)
            }}
        >
            {props.variables.map(v => (
                <SelectOption key={v.id} value={v.name} />
            ))}
        </Select>
    )
}

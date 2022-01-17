import { useState, useEffect, useMemo } from "react"
import { useDispatch } from "react-redux"
import { useParams } from "react-router"
import { useHistory } from "react-router"

import {
    ActionGroup,
    Bullseye,
    Button,
    Card,
    CardBody,
    EmptyState,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    FormSection,
    List,
    ListItem,
    Modal,
    PageSection,
    Popover,
    Spinner,
    TextInput,
} from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import * as api from "./api"
import { TableReport, TableReportConfig, ReportComponent } from "./api"
import TableReportView from "./TableReportView"

import Accessors from "../../components/Accessors"
import OptionalFunction from "../../components/OptionalFunction"
import TestSelect, { SelectedTest } from "../../components/TestSelect"

import { useTester } from "../../auth"
import { alertAction } from "../../alerts"

type ReportConfigComponentProps = {
    component: ReportComponent
    onChange(c: ReportComponent): void
    onDelete(): void
    readOnly: boolean
}

function ReportConfigComponent(props: ReportConfigComponentProps) {
    return (
        <Flex style={{ marginBottom: "10px" }} key={props.component.id}>
            <FlexItem grow={{ default: "grow" }}>
                <FormGroup label="Name" fieldId="name">
                    <TextInput
                        value={props.component.name}
                        onChange={name => props.onChange({ ...props.component, name })}
                        isRequired
                        isReadOnly={props.readOnly}
                    />
                </FormGroup>
                <FormGroup label="Unit" fieldId="unit">
                    <TextInput
                        value={props.component.unit}
                        onChange={unit => props.onChange({ ...props.component, unit })}
                        placeholder="E.g. milliseconds, requests/sec..."
                        isReadOnly={props.readOnly}
                    />
                </FormGroup>
                <FormGroup label="Accessors" fieldId="accessors">
                    <Accessors
                        isReadOnly={props.readOnly}
                        value={props.component.accessors ? props.component.accessors.split(";") : []}
                        onChange={a => props.onChange({ ...props.component, accessors: a.join(";") })}
                    />
                </FormGroup>
                <FormGroup
                    label={
                        <>
                            Function
                            <Popover
                                headerContent="Component transformation function"
                                bodyContent={
                                    <div>
                                        This function should return either single number, array of numbers or an object
                                        with all fields set to number values. In the latter two cases there will be one
                                        table + chart for each member item.
                                    </div>
                                }
                            >
                                <HelpButton />
                            </Popover>
                        </>
                    }
                    fieldId="function"
                >
                    <OptionalFunction
                        func={props.component.function}
                        onChange={f => props.onChange({ ...props.component, function: f })}
                        readOnly={props.readOnly}
                        undefinedText="No function defined."
                        addText="Add component function..."
                        defaultFunc="value => value"
                    />
                </FormGroup>
            </FlexItem>
            {!props.readOnly && (
                <FlexItem alignSelf={{ default: "alignSelfCenter" }}>
                    <Button onClick={() => props.onDelete()}>Delete</Button>
                </FlexItem>
            )}
        </Flex>
    )
}

function HelpButton() {
    return (
        <button
            type="button"
            aria-label="More info"
            onClick={e => e.preventDefault()}
            aria-describedby="simple-form-name-01"
            className="pf-c-form__group-label-help"
        >
            <HelpIcon noVerticalAlign />
        </button>
    )
}

export default function TableReportConfigPage() {
    const { configId: stringId } = useParams<Record<string, string>>()
    const id = parseInt(stringId)
    const history = useHistory()
    const queryParams = new URLSearchParams(history.location.search)
    const reportId = useMemo(() => {
        const edit = queryParams.get("edit")
        return edit ? parseInt(edit) : undefined
    }, [])

    const [config, setConfig] = useState<TableReportConfig>({
        id: -1,
        title: "",
        test: {
            id: -1,
        },
        categoryAccessors: "",
        seriesAccessors: "",
        labelAccessors: "",
        components: [],
    })
    const configValid = config.test?.id && config.test.id >= 0 && config.title && config.seriesAccessors
    const [loading, setLoading] = useState(false)
    const [test, setTest] = useState<SelectedTest>()
    const [preview, setPreview] = useState<TableReport>()
    const [saving, setSaving] = useState(false)

    const dispatch = useDispatch()
    useEffect(() => {
        if (!stringId || stringId === "__new") {
            document.title = "New report | Horreum"
            return
        }
        setLoading(true)
        document.title = "Loading report config ... | Horreum"
        api.getTableConfig(id)
            .then((config: TableReportConfig) => {
                setConfig(config)
                setTest({
                    id: config.test?.id !== undefined ? config.test.id : -1,
                    owner: config.test?.owner,
                    toString: () => config.test?.name || "<deleted test>",
                })
                document.title = "Report " + config.title + " | Horreum"
            })
            .catch(error => {
                dispatch(alertAction("FETCH_REPORT_CONFIG", "Failed to fetch report config.", error))
                document.title = "Error | Horreum"
            })
            .finally(() => setLoading(false))
    }, [id, stringId, dispatch])
    const addComponent = () =>
        setConfig({
            ...config,
            components: [
                ...config.components,
                {
                    id: -1,
                    name: "",
                    order: config.components.length,
                    accessors: "",
                },
            ],
        })

    const isTester = useTester(config?.test?.owner)
    if (loading) {
        return (
            <Bullseye>
                <Spinner size="xl" />
            </Bullseye>
        )
    }
    return (
        <PageSection>
            <Card>
                <CardBody>
                    <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
                        <FormGroup
                            label="Title"
                            isRequired={true}
                            fieldId="test"
                            helperTextInvalid="Name must be unique and not empty"
                        >
                            <TextInput
                                value={config?.title || ""}
                                isRequired
                                type="text"
                                id="title"
                                aria-describedby="title-helper"
                                name="title"
                                isReadOnly={!isTester}
                                validated={config?.title && config.title.trim().length > 0 ? "default" : "error"}
                                onChange={value => {
                                    setConfig({ ...config, title: value })
                                }}
                            />
                        </FormGroup>
                        <FormGroup label="Test" isRequired={true} fieldId="test">
                            <TestSelect
                                selection={test}
                                onSelect={test => {
                                    setTest(test)
                                    setConfig({ ...config, test: { id: test.id } })
                                }}
                                isDisabled={!isTester}
                            />
                        </FormGroup>
                        <FormSection
                            title={
                                <>
                                    Filtering
                                    <Popover
                                        headerContent="Filtering accessors and function"
                                        bodyContent={
                                            <div>
                                                Filtering lets you pre-select which runs are admitted to the report. It
                                                is an optional feature: if you keep the list of accessors empty all runs
                                                will be admitted.
                                                <List>
                                                    <ListItem>
                                                        Accessors select data from the run based on its{" "}
                                                        <NavLink to="/schema">schema</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Filtering function takes the result of single accessor or object
                                                        keyed by accessors (in case of multiple accessors) and returns{" "}
                                                        <code>true</code> if the run should be admitted.
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
                            <FormGroup label="Accessors" fieldId="filterAcessors">
                                <Accessors
                                    value={config?.filterAccessors ? config.filterAccessors.split(";") : []}
                                    onChange={accessors =>
                                        setConfig({ ...config, filterAccessors: accessors.join(";") })
                                    }
                                    isReadOnly={!isTester}
                                />
                            </FormGroup>
                            <FormGroup label="Function" fieldId="filterFunction">
                                <OptionalFunction
                                    func={config?.filterFunction}
                                    onChange={func => setConfig({ ...config, filterFunction: func })}
                                    readOnly={!isTester}
                                    undefinedText="Filtering function not defined."
                                    addText="Add filtering function..."
                                    defaultFunc="value => value"
                                />
                            </FormGroup>
                        </FormSection>
                        <FormSection
                            title={
                                <>
                                    Category
                                    <Popover
                                        headerContent="Category accessors, function and formatter"
                                        bodyContent={
                                            <div>
                                                Categories split runs to several groups where side-by-side comparison
                                                doesn't make sense. For each category and each component (e.g.
                                                'Throughput' or 'CPU Usage') there will be one table/chart of results.
                                                If this is left empty all runs will be grouped into single unnamed
                                                category.
                                                <List>
                                                    <ListItem>
                                                        Accessors select data from the run based on its{" "}
                                                        <NavLink to="/schema">schema</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Category function takes the result of single accessor or object
                                                        keyed by accessors (in case of multiple accessors) and produces
                                                        a value that would be used for the distinction.
                                                    </ListItem>
                                                    <ListItem>
                                                        Category formatter takes the result of category function (or the
                                                        accessors if the function is not present) and formats it for
                                                        presentation.
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
                            <FormGroup label="Accessors" fieldId="categoryAcessors">
                                <Accessors
                                    value={config?.categoryAccessors ? config.categoryAccessors.split(";") : []}
                                    onChange={accessors =>
                                        setConfig({ ...config, categoryAccessors: accessors.join(";") })
                                    }
                                    isReadOnly={!isTester}
                                />
                            </FormGroup>
                            <FormGroup label="Function" fieldId="categoryFunction">
                                <OptionalFunction
                                    func={config?.categoryFunction}
                                    onChange={func => setConfig({ ...config, categoryFunction: func })}
                                    readOnly={!isTester}
                                    undefinedText="Category function not defined."
                                    addText="Add category function..."
                                    defaultFunc="value => value"
                                />
                            </FormGroup>
                            <FormGroup label="Formatter" fieldId="categoryFormatter">
                                <OptionalFunction
                                    func={config?.categoryFormatter}
                                    onChange={func => setConfig({ ...config, categoryFormatter: func })}
                                    readOnly={!isTester}
                                    undefinedText="Category formatter function not defined."
                                    addText="Add category formatter function..."
                                    defaultFunc="category => category"
                                />
                            </FormGroup>
                        </FormSection>
                        <FormSection
                            title={
                                <>
                                    Series
                                    <Popover
                                        headerContent="Series accessors, function and formatter"
                                        bodyContent={
                                            <div>
                                                Series are the products or configurations this report tries to compare,
                                                presented as the columns in table or lines in the chart. Selector for
                                                series is mandatory.
                                                <List>
                                                    <ListItem>
                                                        Accessors select data from the run based on its{" "}
                                                        <NavLink to="/schema">schema</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Series function takes the result of single accessor or object
                                                        keyed by accessors (in case of multiple accessors) and produces
                                                        a value that would be used to select the series.
                                                    </ListItem>
                                                    <ListItem>
                                                        Series formatter takes the result of series function (or the
                                                        accessors if the function is not present) and formats it for
                                                        presentation.
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
                            <FormGroup label="Accessors" fieldId="seriesAcessors">
                                <Accessors
                                    value={config?.seriesAccessors ? config.seriesAccessors.split(";") : []}
                                    onChange={accessors =>
                                        setConfig({ ...config, seriesAccessors: accessors.join(";") })
                                    }
                                    error={config?.seriesAccessors ? undefined : "Selecting series is mandatory."}
                                    isReadOnly={!isTester}
                                />
                            </FormGroup>
                            <FormGroup label="Function" fieldId="seriesFunction">
                                <OptionalFunction
                                    func={config?.seriesFunction}
                                    onChange={func => setConfig({ ...config, seriesFunction: func })}
                                    readOnly={!isTester}
                                    undefinedText="Series function not defined."
                                    addText="Add series function..."
                                    defaultFunc="value => value"
                                />
                            </FormGroup>
                            <FormGroup label="Formatter" fieldId="seriesFormatter">
                                <OptionalFunction
                                    func={config?.seriesFormatter}
                                    onChange={func => setConfig({ ...config, seriesFormatter: func })}
                                    readOnly={!isTester}
                                    undefinedText="Series formatter function not defined."
                                    addText="Add series formatter function..."
                                    defaultFunc="series => series"
                                />
                            </FormGroup>
                        </FormSection>
                        <FormSection
                            title={
                                <>
                                    Labels
                                    <Popover
                                        headerContent="Labels accessors, function and formatter"
                                        bodyContent={
                                            <div>
                                                Labels represent gradation in configuration, e.g. as the cluster is
                                                scaled, load is increased or other attributes are changing. Values with
                                                different labels will be displayed in other rows in the table, and as
                                                datapoints in series in the chart.
                                                <List>
                                                    <ListItem>
                                                        Accessors select data from the run based on its{" "}
                                                        <NavLink to="/schema">schema</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Label function takes the result of single accessor or object
                                                        keyed by accessors (in case of multiple accessors) and produces
                                                        a value that would be used for the label.
                                                    </ListItem>
                                                    <ListItem>
                                                        Label formatter takes the result of label function (or the
                                                        accessors if the function is not present) and formats it for
                                                        presentation.
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
                            <FormGroup label="Accessors" fieldId="labelAccessors">
                                <Accessors
                                    value={config?.labelAccessors ? config.labelAccessors.split(";") : []}
                                    onChange={accessors =>
                                        setConfig({ ...config, labelAccessors: accessors.join(";") })
                                    }
                                    isReadOnly={!isTester}
                                />
                            </FormGroup>
                            <FormGroup label="Function" fieldId="labelFunction">
                                <OptionalFunction
                                    func={config?.labelFunction}
                                    onChange={func => setConfig({ ...config, labelFunction: func })}
                                    readOnly={!isTester}
                                    undefinedText="Label function not defined."
                                    addText="Add label function..."
                                    defaultFunc="value => value"
                                />
                            </FormGroup>
                            <FormGroup label="Formatter" fieldId="labelFormatter">
                                <OptionalFunction
                                    func={config?.labelFormatter}
                                    onChange={func => setConfig({ ...config, labelFormatter: func })}
                                    readOnly={!isTester}
                                    undefinedText="Label formatter function not defined."
                                    addText="Add label formatter function..."
                                    defaultFunc="label => label"
                                />
                            </FormGroup>
                            <FormGroup label="Description" fieldId="labelDescription">
                                <TextInput
                                    value={config?.labelDescription}
                                    onChange={labelDescription => setConfig({ ...config, labelDescription })}
                                    placeholder="Name of the property that this report is scaling."
                                    readOnly={!isTester}
                                />
                            </FormGroup>
                        </FormSection>
                        <FormSection title="Components">
                            {isTester && (
                                <ActionGroup>
                                    <Button onClick={addComponent}>Add component</Button>
                                </ActionGroup>
                            )}
                            {config?.components?.length === 0 && <EmptyState>No components</EmptyState>}
                            {config?.components.map((c, i) => (
                                <ReportConfigComponent
                                    key={i}
                                    component={c}
                                    onChange={updated => {
                                        config.components[i] = updated
                                        setConfig({ ...config })
                                    }}
                                    onDelete={() => {
                                        config.components.splice(i, 1)
                                        config.components.forEach((c, j) => {
                                            c.order = j
                                        })
                                        setConfig({ ...config })
                                    }}
                                    readOnly={!isTester}
                                />
                            ))}
                            {isTester && config?.components?.length > 0 && (
                                <ActionGroup>
                                    <Button onClick={addComponent}>Add component</Button>
                                </ActionGroup>
                            )}
                        </FormSection>
                        {isTester && (
                            <ActionGroup>
                                <Button
                                    isDisabled={!configValid || saving}
                                    onClick={() => {
                                        // TODO save locally for faster reload...
                                        setSaving(true)
                                        api.updateTableConfig(config, reportId)
                                            .then(
                                                report => history.push("/reports/table/" + report.id),
                                                error =>
                                                    dispatch(
                                                        alertAction(
                                                            "SAVE_CONFIG",
                                                            "Failed to save report configuration.",
                                                            error
                                                        )
                                                    )
                                            )
                                            .finally(() => setSaving(false))
                                    }}
                                >
                                    {reportId ? "Save & update report " + reportId : "Save & create new report"}
                                    {saving && <Spinner size="md" />}
                                </Button>
                                <Button
                                    variant="secondary"
                                    isDisabled={!configValid || saving}
                                    onClick={() => {
                                        setSaving(true)
                                        api.previewTableReport(config, reportId)
                                            .then(
                                                report => setPreview(report),
                                                error =>
                                                    dispatch(
                                                        alertAction(
                                                            "PREVIEW_REPORT",
                                                            "Failed to generate report preview.",
                                                            error
                                                        )
                                                    )
                                            )
                                            .finally(() => setSaving(false))
                                    }}
                                >
                                    {reportId ? "Preview updated report" : "Preview"}
                                    {saving && <Spinner size="md" />}
                                </Button>
                            </ActionGroup>
                        )}
                    </Form>
                    {preview && (
                        <Modal isOpen={!!preview} onClose={() => setPreview(undefined)}>
                            <div
                                style={{
                                    overflowY: "auto",
                                    maxHeight: "80vh",
                                    paddingRight: "16px",
                                }}
                            >
                                <TableReportView report={preview} />
                            </div>
                        </Modal>
                    )}
                </CardBody>
            </Card>
        </PageSection>
    )
}

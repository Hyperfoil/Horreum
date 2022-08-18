import { useState, useEffect, useMemo } from "react"
import { useDispatch } from "react-redux"
import { useParams } from "react-router"
import { useHistory } from "react-router"

import {
    ActionGroup,
    Breadcrumb,
    BreadcrumbItem,
    Bullseye,
    Button,
    Card,
    CardBody,
    CardHeader,
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
    Title,
} from "@patternfly/react-core"
import { Link, NavLink } from "react-router-dom"

import Api, { TableReport, TableReportConfig, ReportComponent } from "../../api"
import TableReportView from "./TableReportView"
import ReportLogModal from "./ReportLogModal"

import Labels from "../../components/Labels"
import HelpButton from "../../components/HelpButton"
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
                        id="name"
                        value={props.component.name}
                        onChange={name => props.onChange({ ...props.component, name })}
                        isRequired
                        isReadOnly={props.readOnly}
                    />
                </FormGroup>
                <FormGroup label="Unit" fieldId="unit">
                    <TextInput
                        id="unit"
                        value={props.component.unit}
                        onChange={unit => props.onChange({ ...props.component, unit })}
                        placeholder="E.g. milliseconds, requests/sec..."
                        isReadOnly={props.readOnly}
                    />
                </FormGroup>
                <FormGroup label="Labels" fieldId="labels">
                    <Labels
                        isReadOnly={props.readOnly}
                        labels={props.component.labels}
                        onChange={labels => props.onChange({ ...props.component, labels })}
                        defaultFiltering={false}
                        defaultMetrics={true}
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
                        func={props.component._function}
                        onChange={f => props.onChange({ ...props.component, _function: f })}
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
        filterLabels: [],
        categoryLabels: [],
        seriesLabels: [],
        scaleLabels: [],
        components: [],
    })
    const configValid =
        config.test?.id && config.test.id >= 0 && config.title && config.seriesLabels && config.seriesLabels.length > 0
    const [loading, setLoading] = useState(false)
    const [test, setTest] = useState<SelectedTest>()
    const [preview, setPreview] = useState<TableReport>()
    const [saving, setSaving] = useState(false)
    const [previewLogOpen, setPreviewLogOpen] = useState(false)

    const dispatch = useDispatch()
    useEffect(() => {
        if (!stringId || stringId === "__new") {
            document.title = "New report | Horreum"
            return
        }
        setLoading(true)
        document.title = "Loading report config ... | Horreum"
        Api.reportServiceGetTableReportConfig(id)
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
                    labels: [],
                },
            ],
        })

    const isTester = useTester(config?.test?.owner)

    function saveButton(reportId: number | undefined, label: string, variant: "primary" | "secondary") {
        return (
            <Button
                isDisabled={!configValid || saving}
                variant={variant}
                onClick={() => {
                    // TODO save locally for faster reload...
                    setSaving(true)
                    Api.reportServiceUpdateTableReportConfig(reportId, config)
                        .then(
                            report => history.push("/reports/table/" + report.id),
                            error => dispatch(alertAction("SAVE_CONFIG", "Failed to save report configuration.", error))
                        )
                        .finally(() => setSaving(false))
                }}
            >
                {label}
                {saving && <Spinner size="md" />}
            </Button>
        )
    }

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
                <CardHeader>
                    <Breadcrumb>
                        <BreadcrumbItem>
                            <Link to="/reports">Reports</Link>
                        </BreadcrumbItem>
                        <BreadcrumbItem isActive>
                            {config.id >= 0 ? config.title : "New report configuration"}
                        </BreadcrumbItem>
                    </Breadcrumb>
                </CardHeader>
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
                                    setConfig({
                                        ...config,
                                        test: {
                                            id: test?.id || -1,
                                            name: "",
                                            owner: "",
                                            access: 0,
                                            notificationsEnabled: false,
                                            defaultView: { id: -1, name: "ignored", components: [] },
                                            views: [],
                                        },
                                    })
                                }}
                                isDisabled={!isTester}
                            />
                        </FormGroup>
                        <FormSection
                            title={
                                <>
                                    Filtering
                                    <Popover
                                        headerContent="Filtering labels and function"
                                        bodyContent={
                                            <div>
                                                Filtering lets you pre-select which datasets are admitted to the report.
                                                It is an optional feature: if you keep the list of labels empty all
                                                datasets will be admitted.
                                                <List>
                                                    <ListItem>
                                                        Labels select data from the dataset based on its{" "}
                                                        <NavLink to="/schema">schema(s)</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Filtering function takes the label value (in case of single
                                                        label) or object keyed by label names (in case of multiple
                                                        labels) as its only parameter and returns <code>true</code> if
                                                        the dataset should be admitted.
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
                            <FormGroup label="Labels" fieldId="filterLabels">
                                <Labels
                                    labels={config.filterLabels || []}
                                    onChange={labels => setConfig({ ...config, filterLabels: labels })}
                                    isReadOnly={!isTester}
                                    defaultMetrics={false}
                                    defaultFiltering={true}
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
                                        headerContent="Category labels, function and formatter"
                                        bodyContent={
                                            <div>
                                                Categories split runs to several groups where side-by-side comparison
                                                doesn't make sense. For each category and each component (e.g.
                                                'Throughput' or 'CPU Usage') there will be one table/chart of results.
                                                If this is left empty all runs will be grouped into single unnamed
                                                category.
                                                <List>
                                                    <ListItem>
                                                        Labels select data from the dataset based on its{" "}
                                                        <NavLink to="/schema">schema(s)</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Category function takes the label value (in case of single
                                                        label) or object keyed by label name (in case of multiple
                                                        labels) as its only parameter and produces a value that would be
                                                        used for the distinction.
                                                    </ListItem>
                                                    <ListItem>
                                                        Category formatter takes the result of category function (or
                                                        what would be its input if the function is not present) and
                                                        formats it for presentation.
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
                            <FormGroup label="Labels" fieldId="categoryLabels">
                                <Labels
                                    labels={config.categoryLabels || []}
                                    onChange={labels => setConfig({ ...config, categoryLabels: labels })}
                                    isReadOnly={!isTester}
                                    defaultMetrics={false}
                                    defaultFiltering={true}
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
                                        headerContent="Series labels, function and formatter"
                                        bodyContent={
                                            <div>
                                                Series are the products or configurations this report tries to compare,
                                                presented as the columns in table or lines in the chart. Selector for
                                                series is mandatory.
                                                <List>
                                                    <ListItem>
                                                        Labels select data from the dataset based on its{" "}
                                                        <NavLink to="/schema">schema(s)</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Series function takes the label value (in case of single label)
                                                        or object keyed by label names (in case of multiple labels) as
                                                        its only parameter and produces a value that would be used to
                                                        select the series.
                                                    </ListItem>
                                                    <ListItem>
                                                        Series formatter takes the result of series function (or what
                                                        would be its input if the function is not present) and formats
                                                        it for presentation.
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
                            <FormGroup label="Labels" fieldId="seriesLabels">
                                <Labels
                                    labels={config.seriesLabels || []}
                                    onChange={labels => setConfig({ ...config, seriesLabels: labels })}
                                    error={config.seriesLabels ? undefined : "Selecting series is mandatory."}
                                    isReadOnly={!isTester}
                                    defaultMetrics={false}
                                    defaultFiltering={true}
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
                                    Scale
                                    <Popover
                                        headerContent="Scale labels, function and formatter"
                                        bodyContent={
                                            <div>
                                                Scale represents gradation in configuration, e.g. as the cluster is
                                                scaled, load is increased or other attributes are changing. Values with
                                                different labels will be displayed in other rows in the table, and as
                                                datapoints in series in the chart.
                                                <List>
                                                    <ListItem>
                                                        Labels select data from the dataset based on its{" "}
                                                        <NavLink to="/schema">schema(s)</NavLink>.
                                                    </ListItem>
                                                    <ListItem>
                                                        Scale function takes the label value (in case of single label)
                                                        or object keyed by label names (in case of multiple labels) as
                                                        its only parameter and produces a value that would be used for
                                                        the label.
                                                    </ListItem>
                                                    <ListItem>
                                                        Scale formatter takes the result of scale function (or what
                                                        would be its input if the function is not present) and formats
                                                        it for presentation.
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
                            <FormGroup label="Labels" fieldId="scaleLabels">
                                <Labels
                                    labels={config.scaleLabels || []}
                                    onChange={labels => setConfig({ ...config, scaleLabels: labels })}
                                    isReadOnly={!isTester}
                                    defaultMetrics={false}
                                    defaultFiltering={true}
                                />
                            </FormGroup>
                            <FormGroup label="Function" fieldId="scaleFunction">
                                <OptionalFunction
                                    func={config?.scaleFunction}
                                    onChange={func => setConfig({ ...config, scaleFunction: func })}
                                    readOnly={!isTester}
                                    undefinedText="Label function not defined."
                                    addText="Add label function..."
                                    defaultFunc="value => value"
                                />
                            </FormGroup>
                            <FormGroup label="Formatter" fieldId="scaleFormatter">
                                <OptionalFunction
                                    func={config?.scaleFormatter}
                                    onChange={func => setConfig({ ...config, scaleFormatter: func })}
                                    readOnly={!isTester}
                                    undefinedText="Label formatter function not defined."
                                    addText="Add label formatter function..."
                                    defaultFunc="label => label"
                                />
                            </FormGroup>
                            <FormGroup label="Description" fieldId="scaleDescription">
                                <TextInput
                                    id="description"
                                    value={config?.scaleDescription}
                                    onChange={scaleDescription => setConfig({ ...config, scaleDescription })}
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
                                {reportId && saveButton(reportId, "Update report " + reportId, "primary")}
                                {saveButton(undefined, "Create new report", reportId ? "secondary" : "primary")}
                                <Button
                                    variant="secondary"
                                    isDisabled={!configValid || saving}
                                    onClick={() => {
                                        setSaving(true)
                                        Api.reportServicePreviewTableReport(reportId, config)
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
                        <Modal
                            header={
                                <Flex>
                                    <FlexItem>
                                        <Title headingLevel="h1">Preview</Title>
                                    </FlexItem>
                                    <FlexItem>
                                        <Button onClick={() => setPreviewLogOpen(true)}>Show log</Button>
                                    </FlexItem>
                                </Flex>
                            }
                            isOpen={!!preview}
                            onClose={() => setPreview(undefined)}
                        >
                            <ReportLogModal
                                logs={preview.logs}
                                isOpen={previewLogOpen}
                                onClose={() => setPreviewLogOpen(false)}
                            />
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

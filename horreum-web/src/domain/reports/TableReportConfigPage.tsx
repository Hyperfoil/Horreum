import {useState, useEffect, useMemo, useContext} from "react"
import {useLocation, useParams} from "react-router-dom"
import { useNavigate } from "react-router-dom";

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
    HelperText,
    HelperTextItem,
    FormHelperText,    
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

import {TableReport, TableReportConfig, ReportComponent, reportApi, Access } from "../../api"
import TableReportView from "./TableReportView"
import ReportLogModal from "./ReportLogModal"

import Labels from "../../components/Labels"
import HelpButton from "../../components/HelpButton"
import ExportButton from "../../components/ExportButton"
import OptionalFunction from "../../components/OptionalFunction"
import TestSelect, { SelectedTest } from "../../components/TestSelect"

import { useTester } from "../../auth"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type ReportConfigComponentProps = {
    component: ReportComponent
    onChange(c: ReportComponent): void
    onDelete(): void
    readOnly: boolean
}

function ReportConfigComponent({ component, onChange, onDelete, readOnly }: ReportConfigComponentProps) {
    return (
        <Flex style={{ marginBottom: "10px" }} key={component.id}>
            <FlexItem grow={{ default: "grow" }}>
                <FormGroup label="Name" fieldId="name">
                    <TextInput
                        id="name"
                        value={component.name}
                        onChange={(_event, name) => onChange({ ...component, name })}
                        isRequired
                         readOnlyVariant={readOnly ? "default" : undefined}
                    />
                </FormGroup>
                <FormGroup label="Unit" fieldId="unit">
                    <TextInput
                        id="unit"
                        value={component.unit}
                        onChange={(_event, unit) => onChange({ ...component, unit })}
                        placeholder="E.g. milliseconds, requests/sec..."
                        readOnlyVariant={readOnly ? "default" : undefined}
                    />
                </FormGroup>
                <FormGroup label="Labels" fieldId="labels">
                    <Labels
                        isReadOnly={readOnly}
                        labels={component.labels}
                        onChange={labels => onChange({ ...component, labels })}
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
                        func={component._function}
                        onChange={f => onChange({ ...component, _function: f })}
                        readOnly={readOnly}
                        undefinedText="No function defined."
                        addText="Add component function..."
                        defaultFunc="value => value"
                    />
                </FormGroup>
            </FlexItem>
            {!readOnly && (
                <FlexItem alignSelf={{ default: "alignSelfCenter" }}>
                    <Button onClick={() => onDelete()}>Delete</Button>
                </FlexItem>
            )}
        </Flex>
    )
}

export default function TableReportConfigPage() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { configId } = useParams<string>()
    const id = parseInt(configId ?? "-1")
    const history = useNavigate()
    const location = useLocation()
    const queryParams = new URLSearchParams(location.search)
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

    useEffect(() => {
        if (!configId || configId === "__new") {
            document.title = "New report | Horreum"
            return
        }
        setLoading(true)
        document.title = "Loading report config ... | Horreum"
        reportApi
            .getTableReportConfig(id)
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
                alerting.dispatchError(error, "FETCH_REPORT_CONFIG", "Failed to fetch report config.")
                document.title = "Error | Horreum"
            })
            .finally(() => setLoading(false))
    }, [id, configId])
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
                    reportApi
                        .updateTableReportConfig(reportId, config)
                        .then(
                            report => history(`/test/${test?.id}/reports/table/` + report.id),
                            error => alerting.dispatchError(error,"SAVE_CONFIG", "Failed to save report configuration.")
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
                    <Flex style={{ width: "100%" }} justifyContent={{ default: "justifyContentSpaceBetween" }}>
                        <FlexItem>
                            <Breadcrumb>
                                <BreadcrumbItem>
                                    <Link to={`/test/${test?.id}/#reports-tab`}>Reports</Link>
                                </BreadcrumbItem>
                                <BreadcrumbItem isActive>
                                    {config.id >= 0 ? config.title : "New report configuration"}
                                </BreadcrumbItem>
                            </Breadcrumb>
                        </FlexItem>
                        <FlexItem>
                            <ExportButton
                                name={config?.title || "tablereport"}
                                export={() => reportApi.exportTableReportConfig(id)}
                            />
                        </FlexItem>
                    </Flex>
                </CardHeader>
                <CardBody>
                    <Form isHorizontal={true}>
                        <FormGroup
                            label="Title"
                            isRequired={true}
                            fieldId="test"
                        >
                            <TextInput
                                value={config?.title || ""}
                                isRequired
                                type="text"
                                id="title"
                                aria-describedby="title-helper"
                                name="title"
                                readOnlyVariant={!isTester ? "default" : undefined}
                                validated={config?.title && config.title.trim().length > 0 ? "default" : "error"}
                                onChange={(_event, value) => {
                                    setConfig({ ...config, title: value })
                                }}
                            />
                            <FormHelperText>
                                <HelperText>
                                    <HelperTextItem variant={config?.title && config.title.trim().length > 0 ? "default" : "error"}>
                                    {config?.title && config.title.trim().length > 0 ? "" : "Name must be unique and not empty"}
                                    </HelperTextItem>
                                </HelperText>
                            </FormHelperText>
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
                                            access: Access.Public,
                                            datastoreId: 1,
                                            notificationsEnabled: false,
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
                                    onChange={(_event, scaleDescription) => setConfig({ ...config, scaleDescription })}
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
                                        reportApi
                                            .previewTableReport(reportId, config)
                                            .then(
                                                report => setPreview(report),
                                                error =>
                                                    alerting.dispatchError(
                                                        error,
                                                        "PREVIEW_REPORT",
                                                        "Failed to generate report preview."
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

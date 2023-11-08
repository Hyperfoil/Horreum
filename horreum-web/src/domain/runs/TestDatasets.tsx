import {useCallback, useContext, useEffect, useMemo, useState} from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"
import {
    Breadcrumb,
    BreadcrumbItem,
    Button,
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    ExpandableSection,
    ExpandableSectionToggle,
    Flex,
    FlexItem,
    PageSection,
    Pagination,
    Title,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
} from "@patternfly/react-core"
import { ArrowRightIcon } from "@patternfly/react-icons"
import { Link, NavLink } from "react-router-dom"

import { Duration } from "luxon"
import { toEpochMillis, fingerprintToString } from "../../utils"

import { teamsSelector, teamToName, tokenSelector } from "../../auth"

import Table from "../../components/Table"
import {
    CellProps,
    UseTableOptions,
    UseRowSelectInstanceProps,
    UseRowSelectRowProps,
    Column,
    UseSortByColumnOptions,
} from "react-table"
import {
    DatasetSummary,
    DatasetList,
    SortDirection,
    datasetApi,
    testApi,
    fetchTest,
    Test,
    View, ExportedLabelValues, fetchViews,
} from "../../api"
import { Description, ExecutionTime, renderCell } from "./components"
import SchemaList from "./SchemaList"
import { NoSchemaInDataset } from "./NoSchema"
import ButtonLink from "../../components/ButtonLink"
import LabelsSelect, { SelectedLabels } from "../../components/LabelsSelect"
import ViewSelect from "../../components/ViewSelect"
import AccessIconOnly from "../../components/AccessIconOnly"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type C = CellProps<DatasetSummary> &
    UseTableOptions<DatasetSummary> &
    UseRowSelectInstanceProps<DatasetSummary> & { row: UseRowSelectRowProps<DatasetSummary> }

type DatasetColumn = Column<DatasetSummary> & UseSortByColumnOptions<DatasetSummary>

const staticColumns: DatasetColumn[] = [
    {
        Header: "Run",
        accessor: "runId",
        Cell: (arg: C) => {
            const {
                cell: { value },
            } = arg
            return (
                <>
                    <NavLink to={`/run/${value}#dataset${arg.row.original.ordinal}`}>
                        <ArrowRightIcon />
                        {`\u00A0${value} #${arg.row.original.ordinal + 1}`}
                    </NavLink>
                </>
            )
        },
    },
    {
        Header: "Schema(s)",
        accessor: "schemas",
        disableSortBy: true,
        Cell: (arg: C) => {
            const {
                cell: { value },
            } = arg
            // LEFT JOIN results in schema.id == 0
            if (!value || (value as string[]).length == 0) {
                return <NoSchemaInDataset />
            } else {
                return <SchemaList schemas={value} validationErrors={arg.row.original.validationErrors || []} />
            }
        },
    },
    {
        Header: "Description",
        accessor: "description",
        Cell: (arg: C) => Description(arg.cell.value),
    },
    {
        Header: "Executed",
        accessor: "start",
        Cell: (arg: C) => ExecutionTime(arg.row.original),
    },
    {
        Header: "Duration",
        id: "(stop - start)",
        accessor: dataset =>
            Duration.fromMillis(toEpochMillis(dataset.stop) - toEpochMillis(dataset.start)).toFormat("hh:mm:ss.SSS"),
    },
    {
        Header: "Owner",
        id: "owner",
        accessor: (row: DatasetSummary) => ({
            owner: row.owner,
            access: row.access,
        }),
        Cell: (arg: C) => (
            <>
                {teamToName(arg.cell.value.owner)}
                <span style={{ marginLeft: '8px' }}>
                <AccessIconOnly access={arg.cell.value.access} />
                </span>
            </>
        ),
    },
]

export default function TestDatasets() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { testId: stringTestId } = useParams<any>()
    const testId = parseInt(stringTestId)
    // const [tests, setTests] = useState<Test[] | undefined>(undefined)
    const [test, setTest] = useState<Test | undefined>(undefined)
    console.log(test)
    const [filter, setFilter] = useState<SelectedLabels>()
    const [filterExpanded, setFilterExpanded] = useState(false)
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("start")
    const [direction, setDirection] = useState("Descending")
    const [viewId, setViewId] = useState<number>()
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])

    const [loading, setLoading] = useState(false)
    const [datasets, setDatasets] = useState<DatasetList>()
    const [comparedDatasets, setComparedDatasets] = useState<DatasetSummary[]>()
    const teams = useSelector(teamsSelector)
    const token = useSelector(tokenSelector)

    const [views, setViews] = useState<View[]>([]);

    useEffect(() => {
        fetchTest(testId, alerting)
            .then(setTest)
            .then(() => fetchViews(testId, alerting).then(setViews))
    }, [testId, teams, token])

    useEffect(() => {
        setLoading(true)
        datasetApi.listByTest(
            testId,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
            fingerprintToString(filter),
            pagination.perPage,
            pagination.page,
            pagination.sort,
            viewId
        )
            .then(setDatasets, error =>
                alerting.dispatchError( error, "FETCH_DATASETS", "Failed to fetch datasets in test " + testId)
            )
            .finally(() => setLoading(false))
    }, [ testId, filter, pagination, teams, viewId])
    useEffect(() => {
        document.title = (test?.name || "Loading...") + " | Horreum"
    }, [test])
    const columns = useMemo(() => {
        const allColumns = [...staticColumns]
        if (comparedDatasets) {
            allColumns.unshift({
                Header: "",
                accessor: "id",
                disableSortBy: true,
                Cell: (arg: C) => {
                    if (comparedDatasets.some(ds => ds.id === arg.cell.value)) {
                        return (
                            <Button
                                variant="secondary"
                                onClick={() =>
                                    setComparedDatasets(comparedDatasets.filter(ds => ds.id !== arg.cell.value))
                                }
                            >
                                Remove
                            </Button>
                        )
                    } else {
                        return (
                            <Button onClick={() => setComparedDatasets([...comparedDatasets, arg.row.original])}>
                                Add to comparison
                            </Button>
                        )
                    }
                },
            })
        }
        const view = views?.find(v => v.id === viewId) || views?.find(v => v.name === "Default")
        const components = view?.components || []
        components.forEach(vc => {
            allColumns.push({
                Header: vc.headerName,
                accessor: dataset => dataset.view && dataset.view[vc.id],
                // In general case we would have to calculate the final sortable cell value
                // in database, or fetch all runs and sort in server doing the rendering
                disableSortBy: (!!vc.render && vc.render !== "") || vc.labels.length > 1,
                id: test ? "view_data:" + vc.id + ":" + vc.labels[0] : undefined,
                Cell: renderCell(vc.render, vc.labels.length == 1 ? vc.labels[0] : undefined, token),
            })
        })
        return allColumns
    }, [test, token, comparedDatasets, viewId])

    const flattenLabelValues = (labelValues: Array<ExportedLabelValues>) => {
        const resultArr : any = [];
        labelValues.forEach( (labelValue) => {
            const mappedLabel : any = {};
            labelValue.values?.forEach( (value) => {
                if (value.name !== undefined) {
                    mappedLabel[value.name] = value.value;
                }
            })
            resultArr.push(mappedLabel)
        })
        return resultArr;
    }

    const labelsSource = useCallback(() => {
        return testApi.listLabelValues(testId, true, false)
            .then((result: Array<ExportedLabelValues>) => {
                return flattenLabelValues(result);
            })
    }, [testId, teams, token])
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Toolbar className="pf-u-justify-content-space-between" style={{ width: "100%" }}>
                        <ToolbarGroup>
                            <ToolbarItem style={{ flexGrow: 100 }}>
                                <Breadcrumb>
                                    <BreadcrumbItem>
                                        <Link to="/test">Tests</Link>
                                    </BreadcrumbItem>
                                    <BreadcrumbItem>
                                        {test ? <Link to={"/test/" + test.id}>{test.name}</Link> : "unknown"}
                                    </BreadcrumbItem>
                                    <BreadcrumbItem isActive>Datasets</BreadcrumbItem>
                                </Breadcrumb>
                            </ToolbarItem>
                            <ToolbarItem>
                                <Flex>
                                    <FlexItem>View:</FlexItem>
                                    <FlexItem>
                                        <ViewSelect
                                            views={views || []}
                                            viewId={viewId || views?.find(v => v.name === "Default")?.id || -1}
                                            onChange={setViewId}
                                        />
                                    </FlexItem>
                                </Flex>
                            </ToolbarItem>
                            <ToolbarItem>
                                <ExpandableSectionToggle
                                    isExpanded={filterExpanded}
                                    contentId="filter"
                                    onToggle={setFilterExpanded}
                                >
                                    {filterExpanded ? "Hide filters" : "Show filters"}
                                </ExpandableSectionToggle>
                            </ToolbarItem>
                            <ToolbarItem>
                                <NavLink className="pf-c-button pf-m-primary" to={`/test/${testId}`}>
                                    Edit test
                                </NavLink>
                                <NavLink className="pf-c-button pf-m-secondary" to={`/run/list/${testId}`}>
                                    View runs
                                </NavLink>
                                <Button
                                    variant="secondary"
                                    onClick={() => {
                                        if (comparedDatasets) {
                                            setComparedDatasets(undefined)
                                        } else {
                                            setComparedDatasets([])
                                            setFilterExpanded(true)
                                        }
                                    }}
                                >
                                    {comparedDatasets ? "Cancel comparison" : "Select for comparison"}
                                </Button>
                            </ToolbarItem>
                        </ToolbarGroup>
                    </Toolbar>
                </CardHeader>
                <CardHeader style={{ margin: 0 }}>
                    <ExpandableSection
                        isDetached
                        isExpanded={filterExpanded}
                        contentId="filter"
                        style={{ display: "flex" }}
                    >
                        <LabelsSelect
                            forceSplit={true}
                            fireOnPartial={true}
                            showKeyHelper={true}
                            addResetButton={true}
                            selection={filter}
                            onSelect={setFilter}
                            source={labelsSource}
                            emptyPlaceholder={<span>No filters available</span>}
                        />
                    </ExpandableSection>
                </CardHeader>
                {comparedDatasets && (
                    <CardBody style={{ overflowX: "auto" }}>
                        <Title headingLevel="h3">Datasets for comparison</Title>
                        <Table<DatasetSummary> columns={columns} data={comparedDatasets} isLoading={false} showNumberOfRows={false} />
                        <ButtonLink
                            to={
                                `/dataset/comparison?testId=${testId}&` +
                                comparedDatasets.map(ds => `ds=${ds.id}_${ds.runId}_${ds.ordinal}`).join("&") +
                                "#labels"
                            }
                            isDisabled={comparedDatasets.length === 0}
                        >
                            Compare labels
                        </ButtonLink>
                    </CardBody>
                )}
                <CardHeader style={{ margin: 0, display: "block", textAlign: "right" }}>
                    <Pagination
                        itemCount={datasets?.total}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardHeader>
                <CardBody style={{ overflowX: "auto" }}>
                    <Table
                        columns={columns}
                        data={
                            datasets?.datasets?.filter(ds => !comparedDatasets || !comparedDatasets.includes(ds)) || []
                        }
                        sortBy={[{ id: sort, desc: direction === "Descending" }]}
                        onSortBy={order => {
                            if (order.length > 0 && order[0]) {
                                setSort(order[0].id)
                                setDirection(order[0].desc ? "Descending" : "Ascending")
                            }
                        }}
                        isLoading={loading}
                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={datasets?.total}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardFooter>
            </Card>
        </PageSection>
    )
}

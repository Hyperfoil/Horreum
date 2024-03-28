import {useCallback, useContext, useEffect, useMemo, useState} from "react"
import { useParams } from "react-router-dom"
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
    View,
    ExportedLabelValues,
    fetchViews
} from "../../api"
import { Description, ExecutionTime, renderCell } from "./components"
import SchemaList from "./SchemaList"
import { NoSchemaInDataset } from "./NoSchema"
import ButtonLink from "../../components/ButtonLink"
import LabelsSelect, { SelectedLabels } from "../../components/LabelsSelect"
import ViewSelect from "../../components/ViewSelect"
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type C = CellProps<DatasetSummary> &
    UseTableOptions<DatasetSummary> &
    UseRowSelectInstanceProps<DatasetSummary> & { row: UseRowSelectRowProps<DatasetSummary> }

type DatasetColumn = Column<DatasetSummary> & UseSortByColumnOptions<DatasetSummary>

const staticColumns: DatasetColumn[] = [
    {
        Header: "Data",
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
                    <AccessIcon access={arg.cell.value.access} showText={false} />
                </span>
            </>
        ),
    },
]

export default function TestDatasets() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { testId } = useParams();
    const testIdInt = parseInt(testId ?? "-1")
    // const [tests, setTests] = useState<Test[] | undefined>(undefined)
    const [test, setTest] = useState<Test | undefined>(undefined)
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

    const [views, setViews] = useState<View[]>([])
    useEffect(() => {
        fetchTest(testIdInt, alerting)
            .then(setTest)
            .then(() => fetchViews(testIdInt, alerting).then(setViews))
    }, [testIdInt, teams, token])

    useEffect(() => {
        setLoading(true)
        datasetApi.listByTest(
            testIdInt,
            fingerprintToString(filter),
            pagination.perPage,
            pagination.page,
            pagination.sort,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
            viewId
        )
            .then(setDatasets, error =>
                alerting.dispatchError( error, "FETCH_DATASETS", "Failed to fetch datasets in test " + testIdInt)
            )
            .finally(() => setLoading(false))
    }, [ testIdInt, filter, pagination, teams, viewId])
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
        const view = views?.find(v => v.id === viewId) || views?.at(0)
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
    }, [test, token, comparedDatasets, viewId, views])
    const flattenLabelValues = (labelValues: Array<ExportedLabelValues>) => {
        const resultArr : any = [];
        labelValues.forEach( (labelValue) => {
            resultArr.push(labelValue.values)
        })
        return resultArr;
    }

    const labelsSource = useCallback(() => {
        return testApi.labelValues(testIdInt)
            .then((result: Array<ExportedLabelValues>) => {
                return flattenLabelValues(result);
            })
    }, [testIdInt, teams, token])
    return (
            <Card>
                <CardHeader>
                    <Toolbar className="pf-v5-u-justify-content-space-between" style={{ width: "100%" }}>
                        <ToolbarGroup>
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
                <CardHeader style={{ margin: 0, overflowX: "auto" }}>
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
                                `/dataset/comparison?testId=${testIdInt}&` +
                                comparedDatasets.map(ds => `ds=${ds.id}_${ds.runId}_${ds.ordinal}`).join("&") +
                                "#labels"
                            }
                            isDisabled={comparedDatasets.length === 0}
                        >
                            Compare labels
                        </ButtonLink>
                    </CardBody>
                )}
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
    )
}

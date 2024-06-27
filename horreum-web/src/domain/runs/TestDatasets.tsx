import React from 'react';
import {
    ToolbarContent,
} from '@patternfly/react-core';

import {useCallback, useContext, useEffect, useMemo, useState} from "react"
import { useParams } from "react-router-dom"
import { useSelector } from "react-redux"
import {
    Button,
    Flex,
    FlexItem,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    PageSection,
} from "@patternfly/react-core"
import { ArrowRightIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import { Duration } from "luxon"
import { toEpochMillis, fingerprintToString } from "../../utils"

import { teamsSelector, teamToName, tokenSelector } from "../../auth"

import {
    CellProps,
    UseTableOptions,
    UseRowSelectInstanceProps,
    UseRowSelectRowProps,
    Column,
    UseSortByColumnOptions,
    SortingRule,
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
    fetchViews
} from "../../api"
import { Description, ExecutionTime, renderCell } from "./components"
import ButtonLink from "../../components/ButtonLink"
import ViewSelect from "../../components/ViewSelect"
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import CustomTable from "../../components/CustomTable"
import LabelFilter from "../../components/LabelFilter/LabelFilter";


type DatasetColumn = Column<DatasetSummary> & UseSortByColumnOptions<DatasetSummary>

const staticColumns: DatasetColumn[] = [
    {
        Header: "Data",
        id: "runId",
        accessor: "runId",
        Cell: (arg: CellProps<DatasetSummary>) => {
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
        Cell: (arg: CellProps<DatasetSummary>) => Description(arg.cell.value),
    },
    {
        Header: "Executed",
        id: "start",
        accessor: "start",
        Cell: (arg: CellProps<DatasetSummary>) => ExecutionTime(arg.row.original),
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
        Cell: (arg: CellProps<DatasetSummary>) => (
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
    const [test, setTest] = useState<Test | undefined>(undefined)
    const [filter, setFilter] = useState<any>({})
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sortBy, setSortBy] = useState<SortingRule<DatasetSummary>>({id: "start", desc: true})
    const [viewId, setViewId] = useState<number>()
    const pagination = useMemo(() => ({ page, perPage, sortBy }), [page, perPage, sortBy])
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
        const direction = pagination.sortBy.desc ? SortDirection.Descending : SortDirection.Ascending
        datasetApi.listByTest(
            testIdInt,
            fingerprintToString(filter),
            pagination.perPage,
            pagination.page,
            pagination.sortBy.id,
            direction,
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
                Cell: (arg: CellProps<DatasetSummary>) => {
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
                accessor: dataset => {
                    const elem = dataset.view && dataset.view[vc.id]
                    if (elem && vc.labels.length == 1) {
                        return (elem as any)[vc.labels[0]]
                    }
                    return elem
                },
                // In general case we would have to calculate the final sortable cell value
                // in database, or fetch all runs and sort in server doing the rendering
                disableSortBy: (!!vc.render && vc.render !== "") || vc.labels.length > 1,
                id: test ? "view_data:" + vc.id + ":" + vc.labels[0] : undefined,
                Cell: renderCell(vc.render, vc.labels.length == 1 ? vc.labels[0] : undefined, token),
            })
        })
        return allColumns
    }, [test, token, comparedDatasets, viewId, views])

    const labelsSource = useCallback(() => testApi.filteringLabelValues(testIdInt), [testIdInt, teams, token])

    const arrayOfClearCallbacks : any[] = [];
    const clearCallback = (callback: () => void) => {
        arrayOfClearCallbacks.push(callback);
    }

    const toolbar = (
        <Toolbar
            id="attribute-search-filter-toolbar"
            clearAllFilters={() => {
                for (const key in arrayOfClearCallbacks) {
                    arrayOfClearCallbacks[key]();
                }
                setFilter({});
            }}
        >
            <ToolbarContent>
                <LabelFilter
                    selection={filter}
                    onSelect={setFilter}
                    source={labelsSource}
                    emptyPlaceholder={<span>No filters available</span>}
                    clearCallback={clearCallback}
                />

                <ToolbarItem variant="separator" />
                <ToolbarGroup style={{ float: "right"}}>
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
                        <Button
                            variant="secondary"
                            onClick={() => {
                                if (comparedDatasets) {
                                    setComparedDatasets(undefined)
                                } else {
                                    setComparedDatasets([])
                                }
                            }}
                        >
                            {comparedDatasets ? "Cancel comparison" : "Select for comparison"}
                        </Button>
                    </ToolbarItem>
                </ToolbarGroup>
            </ToolbarContent>
        </Toolbar>
    );

    return (
        <>
            {toolbar}

            {(comparedDatasets) && (
                <PageSection variant="default" isCenterAligned>

                {comparedDatasets && comparedDatasets.length > 0 && (
                    <div style={{ marginTop: "30px"}}>
                        <CustomTable<DatasetSummary> title="Datasets for comparison" columns={columns} data={comparedDatasets} isLoading={false} showNumberOfRows={false} />
                        <ButtonLink
                            to={
                                `/dataset/comparison?testId=${testIdInt}&` +
                                comparedDatasets.map(ds => `ds=${ds.id}_${ds.runId}_${ds.ordinal}`).join("&") +
                                "#labels"
                            }
                            isDisabled={comparedDatasets.length === 0}
                            style={{ marginTop: "15px" }}
                        >
                            Compare labels
                        </ButtonLink>
                    </div>
                )}

                </PageSection>
            )}

            <CustomTable<DatasetSummary>
                columns={columns}
                data={
                    datasets?.datasets?.filter(ds => !comparedDatasets || !comparedDatasets.includes(ds)) || []
                }
                sortBy={[sortBy]}
                onSortBy={order => {
                    if (order.length > 0 && order[0]) {
                        setSortBy(order[0])
                    }
                }}
                isLoading={loading}
                pagination={{
                    top: true,
                    bottom: true,
                    count: datasets?.total,
                    perPage: perPage,
                    page: page,
                    onSetPage: (e, p) => setPage(p),
                    onPerPageSelect: (e, pp) => setPerPage(pp)
                }}
            />
        </>
    )
}

import React from 'react';
import {
 Divider,
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
import { ColumnDef, ColumnSort, createColumnHelper } from '@tanstack/react-table';

const columnHelper = createColumnHelper<DatasetSummary>()

const staticColumns: ColumnDef<DatasetSummary, any>[] = [
    columnHelper.accessor('runId', {
        header: 'Data',
        cell: ({ row }) => <NavLink to={`/run/${row.original.runId}#dataset${row.original.ordinal}`}>
            <ArrowRightIcon />&nbsp;{`${row.original.runId}/${row.original.ordinal}`}
        </NavLink>
    }),
    columnHelper.accessor('description', {
        header: 'Description',
        cell: ({ row }) => Description(row.original.description ?? "")
    }),
    columnHelper.accessor('start', {
        header: 'Executed',
        cell: ({ row }) => ExecutionTime(row.original)
    }),
    columnHelper.accessor((run) => toEpochMillis(run.stop) - toEpochMillis(run.start), {
        header: 'Duration',
        id: "(stop - start)",
        cell: ({ getValue }) => Duration.fromMillis(getValue()).toFormat("hh:mm:ss.SSS")
    }),
    columnHelper.accessor('owner', {
        header: 'Owner',
        cell: ({ row }) => <>
            {teamToName(row.original.owner)}&nbsp;<AccessIcon access={row.original.access} showText={false} />
        </>
    }),
]

export default function TestDatasets() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { testId } = useParams();
    const testIdInt = parseInt(testId ?? "-1")
    const [test, setTest] = useState<Test | undefined>(undefined)
    const [filter, setFilter] = useState<any>({})
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sortBy, setSortBy] = useState<ColumnSort>({id: 'start', desc: true})
    const [viewId, setViewId] = useState<number>()
    const pagination = useMemo(() => ({ page, perPage, sortBy }), [page, perPage, sortBy])
    const [loading, setLoading] = useState(false)
    const [datasets, setDatasets] = useState<DatasetList>()
    const [comparedDatasets, setComparedDatasets] = useState<DatasetSummary[]>([])
    const teams = useSelector(teamsSelector)
    const token = useSelector(tokenSelector)

    const [views, setViews] = useState<View[]>([])
    useEffect(() => {
        fetchTest(testIdInt, alerting)
            .then(setTest)
            .then(() => fetchViews(testIdInt, alerting).then(res => {
                    setViewId(res?.find(v => v.name.toLowerCase() === "default")?.id || -1)
                    return res
                }).then(setViews)
            )
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
        allColumns.unshift(columnHelper.display({
            header: "",
            id: "compare",
            enableSorting: false,
            cell: ({ row }) => {
                const selected = comparedDatasets.some(ds => ds.id === row.original.id)
                return <Button
                        variant="secondary"
                        onClick={() => setComparedDatasets(selected ?
                            comparedDatasets.filter(ds => ds.id !== row.original.id) :
                            [...comparedDatasets, row.original]
                        )}
                        size='sm'
                    >
                        {selected ? 'Remove' : 'Compare'}
                    </Button>
                }
            }
        ));

        (views[views?.findIndex(v => v.id == viewId) ?? 0]?.components || []).forEach(vc => {
            allColumns.push(
                columnHelper.accessor(dataset => {
                    const elem = dataset.view && dataset.view[vc.id]
                    if (elem && vc.labels.length == 1) {
                        return (elem as any)[vc.labels[0]]
                    }
                    return elem
                }, {
                    header: vc.headerName,
                    id: test ? "view_data:" + vc.id + ":" + vc.labels[0] : undefined,
                    cell: renderCell(vc.render, vc.labels.length == 1 ? vc.labels[0] : undefined, token)
                })
            )
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

                    {comparedDatasets.length > 0 && (
                        <ToolbarItem>
                            <Button
                                variant="secondary"
                                onClick={() => {
                                    setComparedDatasets([])
                                }}
                            >
                                Clear comparison
                            </Button>
                        </ToolbarItem>
                    )}
                </ToolbarGroup>
            </ToolbarContent>
        </Toolbar>
    );

    return (
        <>
            {toolbar}

            {(comparedDatasets.length > 0) && (
                <PageSection variant="default" isCenterAligned>

                {comparedDatasets && comparedDatasets.length > 0 && (
                    <div>
                        <CustomTable<DatasetSummary> title="Datasets for comparison" columns={columns} data={comparedDatasets} isLoading={false} showNumberOfRows={false} />
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
                        <Divider style={{ marginTop: "15px" }} />
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

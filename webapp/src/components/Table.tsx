import { useEffect, useState } from "react"
import { Bullseye, Skeleton, Spinner } from "@patternfly/react-core"
import {
    useTable,
    useSortBy,
    useRowSelect,
    Column,
    SortingRule,
    TableState,
    UseRowSelectRowProps,
    UseRowSelectState,
    UseSortByState,
    UseSortByColumnProps,
} from "react-table"
import clsx from "clsx"

import { noop } from "../utils"

// We need to pass the same empty list to prevent re-renders
const NO_DATA: Record<string, unknown>[] = []
const NO_SORT: SortingRule<any>[] = []

// eslint-disable-next-line @typescript-eslint/ban-types
type TableProps<D extends object> = {
    columns: Column<D>[]
    data: D[]
    sortBy: SortingRule<D>[]
    isLoading: boolean
    selected: Record<string, boolean>
    onSelected(ids: Record<string, boolean>): void
    onSortBy?(order: SortingRule<D>[]): void
    showNumberOfRows?: boolean
}

// FIXME: Default values in parameters doesn't work: https://github.com/microsoft/TypeScript/issues/31247
const defaultProps = {
    sortBy: NO_SORT,
    isLoading: false,
    selected: NO_DATA,
    onSelected: noop,
}

// eslint-disable-next-line @typescript-eslint/ban-types
function Table<D extends object>({
    columns,
    data,
    sortBy,
    isLoading,
    selected,
    onSelected,
    onSortBy,
    ...props
}: TableProps<D>) {
    const [currentSortBy, setCurrentSortBy] = useState(sortBy)
    useEffect(() => {
        setCurrentSortBy(sortBy)
    }, [sortBy])
    const { getTableProps, getTableBodyProps, headerGroups, rows, prepareRow, state } = useTable<D>(
        {
            columns,
            data: data || NO_DATA,
            initialState: {
                sortBy: currentSortBy,
                selectedRowIds: selected,
            } as TableState<D>,
        },
        useSortBy,
        useRowSelect
    )
    const rsState = state as UseRowSelectState<D>
    useEffect(() => {
        onSelected(rsState.selectedRowIds)
    }, [rsState.selectedRowIds, onSelected])
    const sortState = state as UseSortByState<D>
    useEffect(() => {
        setCurrentSortBy(sortState.sortBy)
        if (onSortBy && sortState.sortBy) {
            onSortBy(sortState.sortBy)
        }
    }, [sortState.sortBy, onSortBy])
    if (isLoading) {
        return (
            <table className="pf-c-table pf-m-compact pf-m-grid-md" {...getTableProps()}>
                <thead>
                    <tr>
                        <th className={clsx("pf-c-table__sort")}>
                            <button className="pf-c-button pf-m-plain" type="button">
                                Loading... <Spinner size="sm" />
                            </button>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {[...Array(10).keys()].map(i => {
                        return (
                            <tr key={i}>
                                <td>
                                    <Skeleton screenreaderText="Loading..." />
                                </td>
                            </tr>
                        )
                    })}
                </tbody>
            </table>
        )
    }
    if (!data) {
        return (
            <Bullseye>
                <Spinner />
            </Bullseye>
        )
    }
    return (
        <>
            <table className="pf-c-table pf-m-compact pf-m-grid-md" {...getTableProps()}>
                <thead>
                    {headerGroups.map(headerGroup => {
                        return (
                            <tr {...headerGroup.getHeaderGroupProps()}>
                                {headerGroup.headers.map(column => {
                                    const columnProps = column as unknown as UseSortByColumnProps<D>
                                    return (
                                        // Add the sorting props to control sorting. For this example
                                        // we can add them into the header props

                                        <th
                                            className={clsx(
                                                "pf-c-table__sort",
                                                columnProps.isSorted ? "pf-m-selected" : ""
                                            )}
                                            {...column.getHeaderProps(columnProps.getSortByToggleProps())}
                                        >
                                            <button className="pf-c-button pf-m-plain" type="button">
                                                {column.render("Header")}
                                                {/* Add a sort direction indicator */}
                                                {!columnProps.canSort ? (
                                                    ""
                                                ) : (
                                                    <span className="pf-c-table__sort-indicator">
                                                        <i
                                                            className={clsx(
                                                                "fas",
                                                                columnProps.isSorted
                                                                    ? columnProps.isSortedDesc
                                                                        ? "fa-long-arrow-alt-down"
                                                                        : "fa-long-arrow-alt-up"
                                                                    : "fa-arrows-alt-v"
                                                            )}
                                                        ></i>
                                                    </span>
                                                )}
                                            </button>
                                        </th>
                                    )
                                })}
                            </tr>
                        )
                    })}
                </thead>
                <tbody {...getTableBodyProps()}>
                    {rows.map(row => {
                        prepareRow(row)
                        const rowProps = row.getRowProps()
                        if ((row as unknown as UseRowSelectRowProps<D>).isSelected) {
                            rowProps.style = { ...rowProps.style, background: "#EEE" }
                        }
                        return (
                            <tr {...rowProps}>
                                {row.cells.map(cell => {
                                    return (
                                        <td data-label={cell.column.Header} {...cell.getCellProps()}>
                                            {cell.render("Cell")}
                                        </td>
                                    )
                                })}
                            </tr>
                        )
                    })}
                </tbody>
            </table>
            <br />
            {(props.showNumberOfRows === undefined || props.showNumberOfRows) && <div>Showing {rows.length} rows</div>}
        </>
    )
}
Table.defaultProps = defaultProps

export default Table

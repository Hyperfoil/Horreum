import { useEffect, useState } from "react"
import { noop } from "../utils"
import { Card, CardBody, CardFooter, CardHeader, Flex, FlexItem, OnPerPageSelect, OnSetPage, Pagination, PaginationVariant, Skeleton, Spinner, Title, Tooltip } from "@patternfly/react-core"
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table"
import { ThSortType } from "@patternfly/react-table/dist/esm/components/Table/base/types"
import { ColumnDef, flexRender, getCoreRowModel, getSortedRowModel, RowSelectionState, SortingState, useReactTable } from "@tanstack/react-table"

type PaginationProps = {
    top?: boolean
    bottom?: boolean
    count?: number,
    perPage?: number,
    page?: number,
    onSetPage?: OnSetPage
    onPerPageSelect?: OnPerPageSelect,
    variant?: 'top' | 'bottom' | PaginationVariant,
    isCompact?: boolean
}

type CustomTableProps<D extends object> = {
    title?: string
    columns: ColumnDef<D>[]
    data: D[]
    sortBy: SortingState
    isLoading: boolean
    selected?: RowSelectionState
    onSelected(ids: RowSelectionState): void
    onSortBy?(order: SortingState): void
    showNumberOfRows?: boolean,
    cellModifier?: "wrap" | "fitContent" | "breakWord" | "nowrap" | "truncate"
    tableLayout?: "fixed" | "auto"
    pagination?: PaginationProps
}

function CustomTable<D extends object>({
    title,
    columns,
    data,
    sortBy,
    isLoading,
    selected,
    onSelected,
    onSortBy,
    showNumberOfRows,
    cellModifier,
    tableLayout,
    pagination
}: CustomTableProps<D>) {
    const [sorting, setSorting] = useState<SortingState>(sortBy);
    const [rowSelection, setRowSelection] = useState({});

    useEffect(() => onSortBy?.(sorting), [sorting])
    useEffect(() => onSelected?.(rowSelection), [rowSelection])

    const table = useReactTable(
        {
            columns,
            data: data || [],
            initialState: {
                sorting: sortBy,
                rowSelection: selected
            },
            state: {
                sorting,
                rowSelection,
            },
            enableRowSelection: true,
            getCoreRowModel: getCoreRowModel(),
            // getPaginationRowModel: getPaginationRowModel(), // not needed with server pagination
            getSortedRowModel: getSortedRowModel(),
            onSortingChange: setSorting,
            onRowSelectionChange: setRowSelection,
        },
    )

    const renderPagination = (variant?: 'top' | 'bottom' | PaginationVariant, isCompact?: boolean) => pagination && (
        <FlexItem align={{ default: 'alignRight' }}>
            <Pagination
                isCompact={isCompact ?? false}
                itemCount={pagination.count}
                page={pagination.page}
                perPage={pagination.perPage}
                onSetPage={pagination.onSetPage}
                onPerPageSelect={pagination.onPerPageSelect}
                variant={variant ?? PaginationVariant.top}
            />
        </FlexItem>
    );

    return (
        <Card>
            {(title || pagination?.top) && (
                <CardHeader>
                    <Flex>
                        {title && (
                            <Title headingLevel="h3">{title}</Title>
                        )}
                        {renderPagination()}
                    </Flex>
                </CardHeader>
            )}
            <CardBody style={{ overflowX: "auto" }}>
                <Table borders={false} variant="compact" {...table} style={{tableLayout: tableLayout}}>
                    {isLoading && (
                        <Thead>
                            <Tr>
                                <Th>
                                    <span>Loading... <Spinner size="sm"/></span>
                                </Th>
                            </Tr>
                        </Thead>
                    ) || (
                        <Thead>
                            {table.getHeaderGroups().map(headerGroup => {
                                return (
                                    <Tr key={headerGroup.id}>
                                        {headerGroup.headers.map((header, headerIndex) => {
                                            const sortParams: { sort?: ThSortType } = header.column.getCanSort() ? {
                                                sort: {
                                                    sortBy: {
                                                        index: header.column.getIsSorted() ? header.index : -1,
                                                        direction: header.column.getIsSorted() || undefined,
                                                    },
                                                    onSort: header.column.getToggleSortingHandler(),
                                                    columnIndex: header.index
                                                },
                                            } : {}

                                            return (
                                                <Th key={`${headerGroup.id}-h${headerIndex}`}
                                                    aria-label={`column ${header.column.columnDef.header}`}
                                                    modifier={cellModifier ?? "fitContent"}
                                                    isStickyColumn={header.column.getIsPinned() !== false}
                                                    hasLeftBorder={header.column.getIsPinned() === 'right'}
                                                    {...sortParams}
                                                    tooltipProps={{}}
                                                    tooltip={header.column.getCanSort() ? <Tooltip content={`Sort by ${headerIndex}`} /> : undefined}
                                                >
                                                    {flexRender(header.column.columnDef.header, header.getContext())}
                                                </Th>
                                            )
                                        })}
                                    </Tr>
                                )
                            })}
                        </Thead>
                    )}

                    {isLoading && (
                        <Tbody>
                            {[...Array(10).keys()].map(i => {
                                return (
                                    <Tr key={i}>
                                        <Td>
                                            <Skeleton screenreaderText="Loading..." />
                                        </Td>
                                    </Tr>
                                )
                            })}
                        </Tbody>
                    ) || (
                        <Tbody>
                            {table.getRowModel().rows.map(row =>
                                <Tr key={row.id} isRowSelected={row.getIsSelected()}>
                                    {row.getVisibleCells().map(cell => {
                                        return (
                                            <Td key={cell.id}
                                                dataLabel={cell.column.columnDef.header?.toString()}
                                                modifier={cellModifier ?? "fitContent"}
                                                isStickyColumn={cell.column.getIsPinned() !== false}
                                                hasLeftBorder={cell.column.getIsPinned() === 'right'}
                                            >
                                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                            </Td>
                                        )
                                    })}
                                </Tr>
                            )}
                        </Tbody>
                    )}
                </Table>
            </CardBody>
            {(pagination?.bottom || showNumberOfRows === undefined || showNumberOfRows) && (
                <CardFooter>
                    <Flex>
                        {(showNumberOfRows === undefined || showNumberOfRows) && (
                            <FlexItem align={{ default: 'alignLeft' }}>{`Showing ${table.getRowModel().rows.length} rows`}</FlexItem>
                        )}
                        {pagination?.bottom && renderPagination()}
                    </Flex>
                </CardFooter>
            )}
        </Card>
    )
}

CustomTable.defaultProps = {
    sortBy: [],
    isLoading: false,
    selected: [],
    onSelected: noop,
}

export default CustomTable

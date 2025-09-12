import { useEffect, useState } from "react"
import { 
    Column,
    SortingRule,
    TableState,
    UseRowSelectRowProps,
    UseRowSelectState,
    UseSortByColumnProps,
    UseSortByState,
    useRowSelect,
    useSortBy,
    useTable
} from "react-table"
import { noop } from "../utils"
import { Card, CardBody, CardFooter, CardHeader, Flex, FlexItem, OnPerPageSelect, OnSetPage, Pagination, PaginationVariant, Skeleton, Spinner, Title } from "@patternfly/react-core"
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table"
import { ThSortType } from "@patternfly/react-table/dist/esm/components/Table/base/types"

type Direction = 'asc' | 'desc' | undefined;

export type StickyProps = {
    isStickyColumn?: boolean,
    hasLeftBorder?: boolean
}

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
    columns: Column<D>[]
    data: D[]
    sortBy: SortingRule<D>[]
    isLoading: boolean
    selected?: Record<string, boolean>
    onSelected(ids: Record<string, boolean>): void
    onSortBy?(order: SortingRule<D>[]): void
    showNumberOfRows?: boolean,
    cellModifier?: "wrap" | "fitContent" | "breakWord" | "nowrap" | "truncate"
    tableLayout?: "fixed" | "auto"
    pagination?: PaginationProps
}

const NO_DATA: Record<string, unknown>[] = []
const NO_SORT: SortingRule<any>[] = []

const defaultProps = {
    sortBy: NO_SORT,
    isLoading: false,
    selected: NO_DATA,
    onSelected: noop,
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
    const [currentSortBy, setCurrentSortBy] = useState(sortBy)
    const [activeSortIndex, setActiveSortIndex] = useState<number | undefined>();
    const [activeSortDirection, setActiveSortDirection] = useState<Direction>();
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
    const sortState = state as UseSortByState<D>

    // keep active index and direction aligned with the selected sortBy
    const updateActiveIndexes = (selectedSortBy: SortingRule<D> | undefined) => {
        if (selectedSortBy) {
            setActiveSortIndex(columns.findIndex(c => c.id === selectedSortBy.id))
            setActiveSortDirection(selectedSortBy.desc ? "desc" : "asc")
        } else {
            setActiveSortIndex(undefined)
            setActiveSortDirection(undefined)
        }
    }

    useEffect(() => {
        setCurrentSortBy(sortBy)
    }, [sortBy])

    useEffect(() => {
        updateActiveIndexes(currentSortBy.length > 0 ? currentSortBy[0] : undefined)
    }, [currentSortBy])
    
    useEffect(() => {
        setCurrentSortBy(sortState.sortBy)
        if (onSortBy && sortState.sortBy) {
            onSortBy(sortState.sortBy)
        }
    }, [sortState.sortBy])

    useEffect(() => {
        onSelected(rsState.selectedRowIds)
    }, [rsState.selectedRowIds, onSelected])

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
                <Table borders={false} isStriped variant="compact" {...getTableProps()} style={{tableLayout: tableLayout}}>
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
                            {headerGroups.map(headerGroup => {
                                return (
                                    <Tr {...headerGroup.getHeaderGroupProps()}>
                                        {headerGroup.headers.map((col, columnIndex) => {
                                            const columnProps = col as unknown as UseSortByColumnProps<D>
                                            const stickyProps = col as unknown as StickyProps
                                            const sortParams: { sort?: ThSortType } = columnProps.canSort ? {
                                                sort: {
                                                    sortBy: {
                                                    index: activeSortIndex,
                                                    direction: activeSortDirection
                                                    },
                                                    columnIndex,
                                                }
                                            } : {}

                                            return (
                                                <Th
                                                    modifier={cellModifier ?? "fitContent"}
                                                    isStickyColumn={stickyProps.isStickyColumn}
                                                    hasLeftBorder={stickyProps.hasLeftBorder}
                                                    {...sortParams} 
                                                    {...col.getHeaderProps(
                                                        columnProps.getSortByToggleProps(columnProps.canSort ? {title: "Sort by " + col.Header} : {})
                                                    )}
                                                >
                                                    {col.render("Header")}
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
                        <Tbody {...getTableBodyProps()}>
                            {rows.map(row => {
                                prepareRow(row)
                                const rowProps = row.getRowProps()
                                return (
                                    <Tr {...rowProps} isRowSelected={(row as unknown as UseRowSelectRowProps<D>).isSelected}>
                                        {row.cells.map(cell => {
                                            const stickyProps = cell.column as unknown as StickyProps
                                            return (
                                                <Td 
                                                    modifier={cellModifier ?? "fitContent"}
                                                    dataLabel={cell.column.Header?.toString()}
                                                    isStickyColumn={stickyProps.isStickyColumn}
                                                    hasLeftBorder={stickyProps.hasLeftBorder}
                                                    {...cell.getCellProps()}
                                                >
                                                    {cell.render("Cell")}
                                                </Td>
                                            )
                                        })}
                                    </Tr>
                                )
                            })}
                        </Tbody>
                    )}
                </Table>
            </CardBody>
            {(pagination?.bottom || showNumberOfRows === undefined || showNumberOfRows) && (
                <CardFooter>
                    <Flex>
                        {(showNumberOfRows === undefined || showNumberOfRows) && (
                            <FlexItem align={{ default: 'alignLeft' }}>
                                <span>Showing {rows.length} rows</span>
                            </FlexItem>
                        )}
                        {pagination?.bottom && renderPagination()}
                    </Flex>
                </CardFooter>
            )}
        </Card>
    )
}

CustomTable.defaultProps = defaultProps

export default CustomTable

import React, { useEffect } from 'react';
import { Spinner, Bullseye } from '@patternfly/react-core';
import {
  useTable,
  useSortBy,
  useRowSelect,
  Column,
  ColumnInstance,
  SortingRule,
  TableState,
  UseRowSelectRowProps,
  UseRowSelectState,
  UseSortByColumnProps,
} from 'react-table'
import clsx from 'clsx';

// We need to pass the same empty list to prevent re-renders
const NO_DATA: {}[] = []

interface TableColumn<D extends object> extends ColumnInstance<D>, UseSortByColumnProps<D> {}

type TableProps<D extends object> = {
  columns: Column<D>[],
  data: D[],
  initialSortBy: SortingRule<D>[], //TODO
  isLoading: boolean,
  selected: Record<string, boolean>,
  onSelected(ids: Record<string, boolean>): void,
}

// FIXME: Default values in parameters doesn't work: https://github.com/microsoft/TypeScript/issues/31247
const defaultProps = {
  initialSortBy: [],
  isLoading: false,
  selected: NO_DATA,
  onSelected: () => {}
}
function Table<D extends object>({ columns, data, initialSortBy, isLoading, selected, onSelected }: TableProps<D>) {
  const {
    getTableProps,
    getTableBodyProps,
    headerGroups,
    rows,
    prepareRow,
    state,
  } = useTable<D>(
    {
      columns,
      data: data || NO_DATA,
      initialState: {
         sortBy: initialSortBy,
         selectedRowIds: selected,
      } as TableState<D>,
    },
    useSortBy,
    useRowSelect,
  )
  const rsState = state as UseRowSelectState<D>;
  useEffect(()=>{
      onSelected(rsState.selectedRowIds)
  },[rsState.selectedRowIds])
  if (!data) {
     return (
        <Bullseye><Spinner /></Bullseye>
     )
  }
  return (
    <>
      <table className="pf-c-table pf-m-compact pf-m-grid-md" {...getTableProps()}>
        <thead>
          {headerGroups.map((headerGroup,headerGroupIndex) => {
            return (
              <tr {...headerGroup.getHeaderGroupProps()}>
                { headerGroup.headers.map((column,columnIndex) => {
                  const columnProps = (column as unknown) as UseSortByColumnProps<D>
                  return (
                  // Add the sorting props to control sorting. For this example
                  // we can add them into the header props

                  <th className={clsx("pf-c-table__sort", columnProps.isSorted ? "pf-m-selected" : "")}
                      {...column.getHeaderProps(columnProps.getSortByToggleProps())} >
                    <button className="pf-c-button pf-m-plain" type="button">
                      {column.render('Header')}
                      {/* Add a sort direction indicator */}
                      {!columnProps.canSort ? "" : (
                        <span className="pf-c-table__sort-indicator">
                          <i className={clsx("fas", columnProps.isSorted ? (columnProps.isSortedDesc ? "fa-long-arrow-alt-down" : "fa-long-arrow-alt-up") : "fa-arrows-alt-v")}></i>
                        </span>

                      )}
                    </button>
                  </th>
                  )}
                )}
              </tr>
            )
          })}
        </thead>
        <tbody {...getTableBodyProps()}>
          { isLoading &&
          <tr key="loading">
            <td key="loading" colSpan={ columns.length } style={{ textAlign: "center" }}><Spinner size="lg"/></td>
          </tr>
          }
          {rows.map(
            (row, i) => {
              prepareRow(row);
              const rowProps = row.getRowProps()
              if (((row as unknown) as UseRowSelectRowProps<D>).isSelected) {
                rowProps.style = { ...rowProps.style, background: "#EEE" }
              }
              return (
                <tr {...rowProps} >
                  {row.cells.map((cell,cellIndex) => {
                    return (
                      <td data-label={cell.column.Header} {...cell.getCellProps()} >{cell.render('Cell')}</td>
                    )
                  })}
                </tr>
              )
            }
          )}
        </tbody>
      </table>
      <br />
      <div>Showing {rows.length} rows</div>
    </>
  )
}
Table.defaultProps = defaultProps;

export default Table;
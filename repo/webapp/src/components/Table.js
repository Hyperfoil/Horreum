import React from 'react';
import { Spinner } from '@patternfly/react-core';
import { useTable, useSortBy } from 'react-table'
import clsx from 'clsx';

// We need to pass the same empty list to prevent re-renders
const NO_DATA = []

function Table({ columns, data, initialSortBy = [] }) {
  const {
    getTableProps,
    getTableBodyProps,
    headerGroups,
    rows,
    prepareRow,
  } = useTable(
    {
      columns,
      data: data || NO_DATA,
      initialState: {
         sortBy: initialSortBy
      }
    },
    useSortBy
  )
  if (!data) {
     return (
        <center><Spinner /></center>
     )
  }
  return (
    <>
      <table className="pf-c-table pf-m-compact pf-m-grid-md" {...getTableProps()}>
        <thead>
          {headerGroups.map((headerGroup,headerGroupIndex) => {
            return (
              <tr key={headerGroupIndex} {...headerGroup.getHeaderGroupProps()}>
                {headerGroup.headers.map((column,columnIndex) => (
                  // Add the sorting props to control sorting. For this example
                  // we can add them into the header props

                  <th className={clsx("pf-c-table__sort", column.isSorted ? "pf-m-selected" : "")} key={columnIndex} {...column.getHeaderProps(column.getSortByToggleProps())} >
                    <button className="pf-c-button pf-m-plain" type="button">
                      {column.render('Header')}
                      {/* Add a sort direction indicator */}
                      {column.disableSortBy ? "" : (
                        <span className="pf-c-table__sort-indicator">
                          <i className={clsx("fas", column.isSorted ? (column.isSortedDesc ? "fa-long-arrow-alt-down" : "fa-long-arrow-alt-up") : "fa-arrows-alt-v")}></i>
                        </span>

                      )}
                    </button>
                  </th>
                ))}
              </tr>
            )
          })}
        </thead>
        <tbody {...getTableBodyProps()}>
          {rows.map(
            (row, i) => {
              prepareRow(row);
              return (
                <tr key={i} {...row.getRowProps()} >
                  {row.cells.map((cell,cellIndex) => {
                    return (
                      <td data-label={cell.column.Header} key={cellIndex} {...cell.getCellProps()} >{cell.render('Cell')}</td>
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

export default Table;
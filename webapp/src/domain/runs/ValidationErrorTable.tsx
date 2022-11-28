import React, { useMemo } from "react"
import { NavLink } from "react-router-dom"

import { ValidationError } from "../../api"
import { Table, TableBody, TableHeader } from "@patternfly/react-table"

type ValidationErrorTableProps = {
    errors: ValidationError[]
    uris: Record<number, string>
}

export default function ValidationErrorTable(props: ValidationErrorTableProps) {
    const rows = useMemo(
        () =>
            props.errors &&
            props.errors.map(e => ({
                cells: [
                    e.schemaId ? (
                        <NavLink key="schema" to={`/schema/${e.schemaId}`}>
                            {props.uris[e.schemaId] || "unknown schema " + e.schemaId}
                        </NavLink>
                    ) : (
                        "(none)"
                    ),
                    e.error.type,
                    <code>{e.error.path}</code>,
                    <code>{e.error.schemaPath}</code>,
                    <code>{e.error.arguments}</code>,
                    e.error.message,
                ],
            })),
        [props.errors, props.uris]
    )
    return (
        <Table
            aria-label="validation-errors"
            variant="compact"
            cells={["Schema", "Type", "Path", "SchemaPath", "Arguments", "Message"]}
            rows={rows}
        >
            <TableHeader />
            <TableBody />
        </Table>
    )
}

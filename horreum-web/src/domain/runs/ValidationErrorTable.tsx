import { useMemo } from "react"
import { NavLink } from "react-router-dom"

import { SchemaDescriptor, ValidationError } from "../../api"
import {
	Table,
	TableBody,
	TableHeader
} from '@patternfly/react-table/deprecated';

type ValidationErrorTableProps = {
    errors: ValidationError[]
    schemas: SchemaDescriptor[]
}

export default function ValidationErrorTable(props: ValidationErrorTableProps) {
    const rows = useMemo(
        () =>
            props.errors &&
            props.errors.map(e => ({
                cells: [
                    e.schemaId ? (
                        <NavLink key="schema" to={`/schema/${e.schemaId}`}>
                            {props.schemas.find(s => s.id === e.schemaId)?.name || "unknown schema " + e.schemaId}
                        </NavLink>
                    ) : (
                        "(none)"
                    ),
                    e.error.type,
                    <code>{e.error.path}</code>,
                    <code>{e.error.schemaLocation ?? e.error.schemaPath }</code>,
                    <code>{e.error.arguments}</code>,
                    e.error.message,
                ],
            })),
        [props.errors, props.schemas]
    )
    return (
        <Table
            aria-label="validation-errors"
            variant="compact"
            cells={["Schema", "Type", "Path", "Schema Location", "Arguments", "Message"]}
            rows={rows}
        >
            <TableHeader />
            <TableBody />
        </Table>
    )
}

import { useMemo } from "react"
import { NavLink } from "react-router-dom"

import { SchemaDescriptor, ValidationError } from "../../api"
import { Table, TableBody, TableHeader } from "@patternfly/react-table"
import { Json } from "../../generated"

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
                    (e.error as any).type,
                    <code>{(e.error as any).path}</code>,
                    <code>{(e.error as any).schemaPath}</code>,
                    <code>{(e.error as any).arguments}</code>,
                    (e.error as any).message,
                ],
            })),
        [props.errors, props.schemas]
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

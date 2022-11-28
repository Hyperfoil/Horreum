import React from "react"
import { NavLink } from "react-router-dom"
import { Tooltip } from "@patternfly/react-core"
import { interleave } from "../../utils"
import { ValidationError } from "../../generated"
import ErrorBadge from "../../components/ErrorBadge"
import WarnBadge from "../../components/WarnBadge"

type SchemaListProps = {
    schemas: Record<number, string> // id -> uri mapping
    validationErrors: ValidationError[]
}

export default function SchemaList(props: SchemaListProps) {
    let lines = Object.entries(props.schemas).map(([key, uri], i) => {
        const schemaId = parseInt(key)
        const validationErrors = props.validationErrors?.filter(e => e.schemaId === schemaId)
        return (
            <React.Fragment key={2 * i}>
                <NavLink to={`/schema/${key}`}>{uri}</NavLink>{" "}
                {validationErrors.length > 0 && (
                    <Tooltip
                        isContentLeftAligned
                        content={
                            <>
                                There are {validationErrors.length} errors validating the data against this schema:
                                <br />
                                <ul>
                                    {validationErrors.map((e, i) => (
                                        <li key={i}>{e.error.message}</li>
                                    ))}
                                </ul>
                                Visit run/dataset for details.
                            </>
                        }
                    >
                        <ErrorBadge>{validationErrors.length}</ErrorBadge>
                    </Tooltip>
                )}
            </React.Fragment>
        )
    })
    const noSchemaErrors = props.validationErrors?.filter(e => !e.schemaId)
    if (noSchemaErrors.length > 0) {
        lines = [
            ...lines,
            <React.Fragment key="no_schema">
                (none){" "}
                <Tooltip
                    isContentLeftAligned
                    content={
                        <>
                            There are {noSchemaErrors.length} errors validating the data against this schema:
                            <br />
                            <ul>
                                {noSchemaErrors.map((e, i) => (
                                    <li key={i}>{e.error.message}</li>
                                ))}
                            </ul>
                            Visit run/dataset for details.
                        </>
                    }
                >
                    <WarnBadge>{noSchemaErrors.length}</WarnBadge>
                </Tooltip>
            </React.Fragment>,
        ]
    }
    return (
        <>
            {interleave(lines, i => (
                <br key={2 * i + 1} />
            ))}
        </>
    )
}

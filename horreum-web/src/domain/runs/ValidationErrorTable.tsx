import { NavLink } from "react-router-dom"

import { SchemaDescriptor, ValidationError } from "../../api"
import {Table, Tbody, Td, Th, Thead, Tr} from "@patternfly/react-table";

type ValidationErrorTableProps = {
    errors: ValidationError[]
    schemas: SchemaDescriptor[]
}

export default function ValidationErrorTable(props: ValidationErrorTableProps) {
    return (
        props.errors &&
        <Table aria-label="validation-errors" variant="compact" isStickyHeader>
            <Thead>
                <Tr>
                    {["Schema", "Type", "Path", "Schema Location", "Arguments", "Message"].map((col, index) =>
                        <Th key={index} aria-label={"header-" + index}>{col}</Th>
                    )}
                </Tr>
            </Thead>
            <Tbody>
                {props.errors.map((error, index) =>
                    <Tr key={index}>
                        <Td key="Schema">{error.schemaId ?
                            <NavLink key="schema" to={`/schema/${error.schemaId}`}>
                                {props.schemas.find(s => s.id === error.schemaId)?.name || "unknown schema " + error.schemaId}
                            </NavLink>
                            : "None"}
                        </Td>
                        <Td key="Type">{error.error.type}</Td>
                        <Td key="Path"><code>{error.error.path}</code></Td>
                        <Td key="Schema Location"><code>{error.error.schemaLocation ?? error.error.schemaPath }</code></Td>
                        <Td key="Arguments"><code>{error.error.arguments}</code></Td>
                        <Td key="Message">{error.error.message}</Td>
                    </Tr>
                )}
            </Tbody>
        </Table>
    )
}

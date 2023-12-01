import { Tooltip } from "@patternfly/react-core"
import { ExclamationTriangleIcon } from "@patternfly/react-icons"

export function NoSchemaInRun() {
    return (
        <Tooltip
            content={
                <>
                    This run does not contain any reference to schema known by Horreum, therefore the single dataset
                    will be empty. If the JSON is annotated with <code>$schema</code> please create a matching Schema.
                </>
            }
        >
            <span style={{ verticalAlign: "bottom" }}>
                <ExclamationTriangleIcon style={{ fill: "var(--pf-v5-global--warning-color--100)" }} /> No schema
            </span>
        </Tooltip>
    )
}

export function NoSchemaInDataset() {
    return (
        <Tooltip
            content={
                <>
                    This dataset does not contain any reference to schema known by Horreum. Please make sure that the
                    original run refers to an existing schema and transformer(s) results use <code>$schema</code>, too.
                    a matching Schema.
                </>
            }
        >
            <span style={{ verticalAlign: "bottom" }}>
                <ExclamationTriangleIcon style={{ fill: "var(--pf-v5-global--warning-color--100)" }} /> No schema
            </span>
        </Tooltip>
    )
}

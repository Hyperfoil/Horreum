import { ReactElement } from "react"
import { Badge } from "@patternfly/react-core"
import { ExclamationTriangleIcon } from "@patternfly/react-icons"

type ErrorBadgeProps = {
    children: string | number | ReactElement<any>
}

export default function ErrorBadge({children}: ErrorBadgeProps) {
    return (
        <Badge
            style={{
                background: "var(--pf-t--global--color--status--warning--default)",
                color: "var(--pf-t--global--text--color--status--on-warning--default",
            }}
        >
            <ExclamationTriangleIcon
                style={{
                    fill: "var(--pf-t--global--icon--color--status--warning--default)",
                    marginTop: "4px",
                }}
            />{" "}
            {children}
        </Badge>
    )
}

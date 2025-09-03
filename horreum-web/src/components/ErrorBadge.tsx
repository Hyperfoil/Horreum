import { ReactElement } from "react"
import { Badge } from "@patternfly/react-core"
import { ExclamationCircleIcon } from "@patternfly/react-icons"

type ErrorBadgeProps = {
    children: string | number | ReactElement
}

export default function ErrorBadge({ children }: ErrorBadgeProps) {
    return (
        <Badge
            style={{
                background: "var(--pf-t--global--color--status--danger--default)",
                color: "var(--pf-t--global--text--color--status--on-danger--default)",
            }}
        >
            <ExclamationCircleIcon
                style={{
                    fill: "var(--pf-t--global--icon--color--status--danger--default)",
                    marginTop: "4px",
                }}
            />{" "}
            {children}
        </Badge>
    )
}

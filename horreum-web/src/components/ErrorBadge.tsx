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
                background: "var(--pf-global--palette--red-50)",
                color: "var(--pf-global--Color--200)",
            }}
        >
            <ExclamationCircleIcon
                style={{
                    fill: "var(--pf-global--danger-color--100)",
                    marginTop: "4px",
                }}
            />{" "}
            {children}
        </Badge>
    )
}

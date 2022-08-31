import { ReactElement } from "react"
import { Badge } from "@patternfly/react-core"
import { ExclamationTriangleIcon } from "@patternfly/react-icons"

type ErrorBadgeProps = {
    children: string | number | ReactElement
}

export default function ErrorBadge(props: ErrorBadgeProps) {
    return (
        <Badge
            style={{
                background: "var(--pf-global--palette--orange-50)",
                color: "var(--pf-global--Color--200)",
            }}
        >
            <ExclamationTriangleIcon
                style={{
                    fill: "var(--pf-global--warning-color--100)",
                    marginTop: "4px",
                }}
            />{" "}
            {props.children}
        </Badge>
    )
}

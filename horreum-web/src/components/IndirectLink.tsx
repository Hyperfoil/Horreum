import { HTMLProps, ReactNode } from "react"
import { useHistory } from "react-router-dom"

import { noop } from "../utils"
import { Button } from "@patternfly/react-core"

type IndirectLinkProps = {
    variant?: "primary" | "secondary" | "tertiary" | "control" | "link" | "plain"
    children: ReactNode | ReactNode[]
    onNavigate(): Promise<string>
} & Omit<HTMLProps<HTMLButtonElement>, "ref">

export default function IndirectLink({ variant = "link", onNavigate, children, ...props }: IndirectLinkProps) {
    const history = useHistory()
    return (
        <Button
            {...props}
            type="button"
            variant={variant}
            onClick={() =>
                onNavigate()
                    .then(path => history.push(path))
                    .catch(noop)
            }
        >
            {children}
        </Button>
    )
}

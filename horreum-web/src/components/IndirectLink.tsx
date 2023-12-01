import { HTMLProps, ReactNode } from "react"
import { useNavigate } from "react-router-dom"

import { noop } from "../utils"
import { Button, ButtonProps } from "@patternfly/react-core"

type IndirectLinkProps = {
    variant?: "primary" | "secondary" | "tertiary" | "control" | "link" | "plain"
    children: ReactNode | ReactNode[]
    onNavigate(): Promise<string>
} & ButtonProps //Omit<HTMLProps<HTMLButtonElement>, "ref"|"size">

export default function IndirectLink({ variant = "link", onNavigate, children, ...props }: IndirectLinkProps) {
    const navigate  = useNavigate()
    return (
        <Button
            {...props}
            type="button"
            variant={variant}
            onClick={() =>
                onNavigate()
                    .then((path) => navigate(path))
                    .catch(noop)
            }
        >
            {children}
        </Button>
    )
}

import { ReactNode } from "react"

import { Bullseye, Spinner } from "@patternfly/react-core"

export default function MaybeLoading(props: {
    loading: boolean
    size?: "sm" | "md" | "lg" | "xl"
    children: ReactNode | ReactNode[]
}) {
    if (!props.loading) return <>{props.children}</>
    return (
        <Bullseye>
            <Spinner size={props.size || "xl"} />
        </Bullseye>
    )
}

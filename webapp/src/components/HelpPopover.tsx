import { ReactNode } from "react"

import { Popover } from "@patternfly/react-core"
import HelpButton from "./HelpButton"

type HelpPopoverProps = {
    header?: ReactNode
    text: ReactNode
}

export default function HelpPopover(props: HelpPopoverProps) {
    return (
        <Popover headerContent={props.header} bodyContent={props.text}>
            <HelpButton />
        </Popover>
    )
}

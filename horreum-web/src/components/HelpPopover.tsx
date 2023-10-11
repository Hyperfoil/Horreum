import { ReactNode } from "react"

import { Popover } from "@patternfly/react-core"
import HelpButton from "./HelpButton"

type HelpPopoverProps = {
    header?: ReactNode
    text: ReactNode
}

export default function HelpPopover({header, text}: HelpPopoverProps) {
    return (
        <Popover headerContent={header} bodyContent={text}>
            <HelpButton />
        </Popover>
    )
}

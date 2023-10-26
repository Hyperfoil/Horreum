import { Tooltip } from "@patternfly/react-core"
import { LockedIcon } from "@patternfly/react-icons"
import { Access } from "../api"

export default function AccessIcon({ access }: { access: Access }) {
    let color
    let text
    let explanation
    switch (access) {
        case Access.Public: {
            color = "--pf-global--success-color--200"
            text = "Public"
            explanation = "Anyone can view this."
            break
        }
        case Access.Protected: {
            color = "--pf-global--warning-color--100"
            text = "Protected"
            explanation = "Only authenticated (logged in) users can view this."
            break
        }
        case Access.Private: {
            color = "--pf-global--danger-color--100"
            text = "Private"
            explanation = "Only users from the owning team can view this."
            break
        }
        default: {
            color = "--pf-global--icon--Color--light"
            text = "Unknown"
            explanation = "Unknown"
            break
        }
    }
    return (
        <>
            <LockedIcon style={{ fill: "var(" + color + ")" }} />
            {"\u00A0"}
            <Tooltip content={explanation}>
                <span>{text}</span>
            </Tooltip>
        </>
    )
}

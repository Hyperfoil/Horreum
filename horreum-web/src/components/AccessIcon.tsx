import { Tooltip } from "@patternfly/react-core"
import { LockedIcon } from "@patternfly/react-icons"
import { Access } from "../api"

export default function AccessIcon({ access, showText = true }: { access: Access, showText ?: boolean }) {
    let color
    let text
    let explanation
    switch (access) {
        case Access.Public: {
            color = "--pf-v5-global--success-color--200"
            text = "Public"
            explanation = "Anyone can view this."
            break
        }
        case Access.Protected: {
            color = "--pf-v5-global--warning-color--100"
            text = "Protected"
            explanation = "Only authenticated (logged in) users can view this."
            break
        }
        case Access.Private: {
            color = "--pf-v5-global--danger-color--100"
            text = "Private"
            explanation = "Only users from the owning team can view this."
            break
        }
        default: {
            color = "--pf-v5-global--icon--Color--light"
            text = "Unknown"
            explanation = "Unknown"
            break
        }
    }
    return (
        <>
            <Tooltip content={explanation}>
                <>
                    <LockedIcon style={{ fill: "var(" + color + ")" }} />
                    {"\u00A0"}
                    {showText ? <span>{text}</span> : <></>}
                </>
            </Tooltip>
        </>
    )
}

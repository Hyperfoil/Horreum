import { Tooltip } from "@patternfly/react-core"
import { LockedIcon } from "@patternfly/react-icons"
import { Access } from "../api"

export default function AccessIcon({ access }: { access: Access }) {
    let color
    let explanation
    switch (access) {
        case Access.Public: {
            color = "--pf-v5-global--success-color--200"
            explanation = "Anyone can view this."
            break
        }
        case Access.Protected: {
            color = "--pf-v5-global--warning-color--100"
            explanation = "Only authenticated (logged in) users can view this."
            break
        }
        case Access.Private: {
            color = "--pf-v5-global--danger-color--100"
            explanation = "Only users from the owning team can view this."
            break
        }
        default: {
            color = "--pf-v5-global--icon--Color--light"
            explanation = "Unknown"
            break
        }
    }
    return (
        <>
            <LockedIcon style={{ fill: "var(" + color + ")" }} />
            {"\u00A0"}
            <Tooltip content={explanation} />     
        </>
    )
}

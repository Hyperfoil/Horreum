import { Icon } from '@patternfly/react-core';
import { HelpIcon } from "@patternfly/react-icons"

export default function HelpButton() {
    return (
        <button
            type="button"
            aria-label="More info"
            onClick={e => e.preventDefault()}
            className="pf-v6-c-form__group-label-help"
            style={{ background: "none", border: "none", outline: "none" }}
        >
            <Icon isInline={true}>
                <HelpIcon/>
            </Icon>
        </button>
    )
}

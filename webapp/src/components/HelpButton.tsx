import { HelpIcon } from "@patternfly/react-icons"

export default function HelpButton() {
    return (
        <button
            type="button"
            aria-label="More info"
            onClick={e => e.preventDefault()}
            className="pf-c-form__group-label-help"
        >
            <HelpIcon noVerticalAlign />
        </button>
    )
}

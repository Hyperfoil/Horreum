import { HelpIcon } from "@patternfly/react-icons"

export default function HelpButton() {
    return (
        <button
            type="button"
            aria-label="More info"
            onClick={e => e.preventDefault()}
            aria-describedby="simple-form-name-01"
            className="pf-c-form__group-label-help"
        >
            <HelpIcon noVerticalAlign />
        </button>
    )
}

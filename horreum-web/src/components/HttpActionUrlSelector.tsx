import {useContext, useEffect, useState} from "react"
import {
	FormGroup,
    FormHelperText,
	InputGroup,
    InputGroupItem,
    HelperText,
    HelperTextItem,
    TextInput,
} from '@patternfly/react-core';

import { AllowedSite, getAllowedSites} from "../api"
import {AppContext} from "../context/AppContext";
import {AppContextType} from "../context/@types/appContextTypes";
import { SimpleSelect } from "./templates/SimpleSelect";

function isValidUrl(url: string) {
    try {
        new URL(url)
        return true
    } catch (_) {
        return false
    }
}

type HttpActionUrlSelectorProps = {
    active: boolean
    value: string
    setValue(value: string): void
    isDisabled?: boolean
    isReadOnly?: boolean
    setValid?(valid: boolean): void
    extraCheck?(value: string): string | boolean
}

export default function HttpActionUrlSelector({ active, value, setValue, isDisabled, isReadOnly, setValid, extraCheck}: HttpActionUrlSelectorProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [prefixes, setPrefixes] = useState<AllowedSite[]>([{ id: -1, prefix: "" }])
    useEffect(() => {
        if (active) {
            getAllowedSites(alerting).then(setPrefixes)
        }
    }, [active])

    const isUrlValid = isValidUrl(value)
    const isUrlAllowed = prefixes.some(p => value.startsWith(p.prefix))
    const extraCheckResult = extraCheck ? extraCheck(value) : true
    return (
        <FormGroup
            label="HTTP Action URL"
            isRequired={true}
            fieldId="url"
        >
            <InputGroup>
                {!isReadOnly &&
                    <SimpleSelect
                        placeholder="Pick URL prefix"
                        initialOptions={prefixes.map(p => ({value: p.prefix, content: p.prefix, selected: false}))}
                        onSelect={(_, item) => setValue(item as string)}
                    />
                }
                <InputGroupItem isFill ><TextInput
                    value={value}
                    isRequired
                    type="text"
                    id="url"
                    aria-describedby="url-helper"
                    name="url"
                    validated={isUrlValid && isUrlAllowed && extraCheckResult === true ? "default" : "error"}
                    placeholder="e.g. 'http://example.com/api/action'"
                    onChange={(_event, value) => {
                        value = value.trim()
                        if (setValid) {
                            setValid(
                                isValidUrl(value) &&
                                    prefixes.some(p => value.startsWith(p.prefix)) &&
                                    (!extraCheck || extraCheck(value) === true)
                            )
                        }
                        setValue(value)
                    }}
                    isDisabled={isDisabled}
                     readOnlyVariant={isReadOnly ? "default" : undefined}
                /></InputGroupItem>
            </InputGroup>
            <FormHelperText>
                <HelperText>
                    <HelperTextItem variant={isUrlValid && isUrlAllowed && extraCheckResult === true ? "default" : "error"}>
                    {isUrlValid && isUrlAllowed && extraCheckResult === true ? "URL (with protocol) for POST callback" : !isUrlValid
                        ? "URL cannot be parsed."
                        : !isUrlAllowed
                        ? "URL does not start with any of the allowed prefixes."
                        : extraCheckResult ? "URL (with protocol) for POST callback" : ""}
                    </HelperTextItem>
                </HelperText>
            </FormHelperText>
        </FormGroup>
    )
}

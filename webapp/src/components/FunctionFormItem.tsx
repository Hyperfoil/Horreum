import { ReactNode } from "react"
import { Button, FormGroup, Popover } from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"

import Editor from "./Editor/monaco/Editor"

type JsFunctionProps = {
    label: string
    helpText: ReactNode
    value: string | undefined
    onChange(func: string): void
    readOnly: boolean
}

export default function FunctionFormItem(props: JsFunctionProps) {
    return (
        <FormGroup
            style={{ display: "inline", marginTop: 0 }}
            label={props.label}
            labelIcon={
                <Popover minWidth="50vw" maxWidth="50vw" bodyContent={props.helpText}>
                    <Button variant="plain" onClick={e => e.preventDefault()}>
                        <HelpIcon />
                    </Button>
                </Popover>
            }
            fieldId="function"
        >
            <div
                style={{
                    minHeight: "100px",
                    height: "100px",
                    resize: "vertical",
                    overflow: "auto",
                }}
            >
                <Editor
                    value={
                        !props.value
                            ? ""
                            : typeof props.value === "string"
                            ? props.value
                            : (props.value as any).toString()
                    }
                    onChange={props.onChange}
                    language="typescript"
                    options={{
                        wordWrap: "on",
                        wrappingIndent: "DeepIndent",
                        readOnly: props.readOnly,
                    }}
                />
            </div>
        </FormGroup>
    )
}

import { Button } from "@patternfly/react-core"
import { AddCircleOIcon } from "@patternfly/react-icons"
import Editor from "./Editor/monaco/Editor"

type OptionalFunctionProps = {
    func: string | undefined | null
    onChange(value: string): void
    readOnly: boolean
    undefinedText: string
    addText: string
    defaultFunc: string
}

export default function OptionalFunction(props: OptionalFunctionProps) {
    if (props.func === undefined || props.func === null) {
        if (props.readOnly) {
            return <>{props.undefinedText} </>
        } else {
            return (
                <Button
                    style={{ padding: 0, marginTop: "6px" }}
                    variant="link"
                    onClick={() => {
                        props.onChange(props.defaultFunc)
                    }}
                >
                    <AddCircleOIcon />
                    {"\u00A0"}
                    {props.addText}
                </Button>
            )
        }
    } else {
        // failsafe if the wrong type gets passed in
        const funcAsString = typeof props.func === "string" ? props.func : (props.func as any).toString()
        return (
            <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                <Editor
                    value={funcAsString}
                    onChange={value => {
                        props.onChange(value?.trim() || "")
                    }}
                    language="typescript"
                    options={{
                        wordWrap: "on",
                        wrappingIndent: "DeepIndent",
                        readOnly: props.readOnly,
                    }}
                />
            </div>
        )
    }
}

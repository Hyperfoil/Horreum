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

export default function OptionalFunction({func, onChange, readOnly, undefinedText, addText, defaultFunc}: OptionalFunctionProps) {
    if (func === undefined || func === null) {
        if (readOnly) {
            return <>{undefinedText} </>
        } else {
            return (
                <Button
                    icon={<AddCircleOIcon/>}
                    style={{padding: 0, marginTop: "6px"}}
                    variant="link"
                    onClick={() => {
                        onChange(defaultFunc)
                    }}
                >
                    {addText}
                </Button>
            )
        }
    } else {
        // failsafe if the wrong type gets passed in
        const funcAsString = typeof func === "string" ? func : (func as any).toString()
        return (
            <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                <Editor
                    value={funcAsString}
                    onChange={value => {
                    onChange(value?.trim() || "")
                    }}
                    language="typescript"
                    options={{
                        wordWrap: "on",
                        wrappingIndent: "DeepIndent",
                        readOnly: readOnly,
                    }}
                />
            </div>
        )
    }
}

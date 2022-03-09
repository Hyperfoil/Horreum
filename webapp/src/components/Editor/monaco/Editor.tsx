import { useRef } from "react"

import MonacoEditor, { useMonaco, OnMount } from "@monaco-editor/react"
import { editor } from "monaco-editor/esm/vs/editor/editor.api"

type EditorProps = {
    value?: string
    language?: string
    options: any
    onChange?(value: string | undefined): void
    height?: number | string
}

export default function Editor(props: EditorProps) {
    const monaco = useMonaco()
    const valueGetter = useRef<() => string>()

    const onMount: OnMount = (editor: editor.IStandaloneCodeEditor) => {
        valueGetter.current = () => editor.getValue()
        if (!monaco) {
            return
        }
        editor.addAction({
            id: "my-unique-id",
            label: "my label",
            keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KEY_S],
            precondition: undefined,
            keybindingContext: undefined,
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: ed => {
                console.log("Ctrl+S => " + ed.getPosition())
            },
        })
    }

    return (
        <MonacoEditor
            value={props.value}
            language={props.language || "json"}
            theme="vs-dark"
            options={{
                //renderLineHighlight : 'none',
                ...props.options,
                language: props.language || "json",
                automaticLayout: true,
                scrollBeyondLastLine: false,
            }}
            height={props.height}
            onMount={onMount}
            onChange={props.onChange}
        />
    )
}
